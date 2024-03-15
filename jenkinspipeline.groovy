pipeline {
    agent any

    environment {
        // Define AWS region and AMI ID
        AWS_REGION = 'your_aws_region'
        AMI_ID = 'your_ami_id'
    }

    stages {
        stage('Deploy EC2 instances') {
            steps {
                script {
                    // Use AWS SDK for Jenkins to spin up EC2 instances
                    def instanceIds = awsEC2CreateInstance(ami: "${AMI_ID}", region: "${AWS_REGION}", count: 6)

                    // Store instance IDs for future reference
                    env.INSTANCE_IDS = instanceIds.join(',')
                }
            }
        }
        stage('Install Prometheus and Grafana') {
            steps {
                script {
                    // Install Prometheus
                    sshScript remote: 'your_ec2_instance_ip', user: 'ec2-user', password: 'your_ec2_instance_password', script: '''
                        wget https://github.com/prometheus/prometheus/releases/download/v2.30.3/prometheus-2.30.3.linux-amd64.tar.gz
                        tar xvfz prometheus-2.30.3.linux-amd64.tar.gz
                        cd prometheus-2.30.3.linux-amd64
                        # Configure prometheus.yml as per your setup
                        ./prometheus --config.file=prometheus.yml
                    '''

                    // Install Grafana
                    sshScript remote: 'your_ec2_instance_ip', user: 'ec2-user', password: 'your_ec2_instance_password', script: '''
                        wget https://dl.grafana.com/oss/release/grafana-8.2.2-1.x86_64.rpm
                        sudo yum localinstall grafana-8.2.2-1.x86_64.rpm -y
                        sudo systemctl start grafana-server
                        sudo systemctl enable grafana-server
                    '''
                }
            }
        }
        stage('Configure Monitoring') {
            steps {
                script {
                    // Configure Prometheus to scrape metrics from EC2 instances
                    sshScript remote: 'your_ec2_instance_ip', user: 'ec2-user', password: 'your_ec2_instance_password', script: '''
                        # Example: Edit prometheus.yml to add EC2 instance as a target for scraping
                        echo "  - targets: ['your_ec2_instance_ip:9100']" >> prometheus.yml
                        # Reload Prometheus configuration
                        curl -X POST http://localhost:9090/-/reload
                    '''

                    // Configure Grafana to visualize Prometheus metrics (assuming Grafana is running on port 3000)
                    sshScript remote: 'your_ec2_instance_ip', user: 'ec2-user', password: 'your_ec2_instance_password', script: '''
                        # Example: Use Grafana API to provision Prometheus as a data source
                        curl -X POST -H "Content-Type: application/json" -d '{"name":"Prometheus","type":"prometheus","url":"http://localhost:9090","access":"proxy","isDefault":true}' http://localhost:3000/api/datasources
                    '''
                }
            }
        }
    }

    post {
        always {
            // Clean up resources if needed
            script {
                def instanceIds = env.INSTANCE_IDS.split(',')
                awsEC2TerminateInstances(instanceIds: instanceIds, region: "${AWS_REGION}")
            }
        }
    }
}
