/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurator;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.configuration.deployment.DeploymentSourceType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public class DeployToServerConfigurationType extends ConfigurationTypeBase {
  private final DeployToServerConfigurationType.DeployToServerConfigurationFactory myFactory;

  public static String getId(ServerType<?> serverType) {
    return serverType.getId() + "-deploy";
  }

  private final ServerType<?> myServerType;

  public DeployToServerConfigurationType(@NotNull ServerType<?> serverType) {
    super(getId(serverType), serverType.getPresentableName() + " Deployment",
          "Deploy to " + serverType.getPresentableName() + " run configuration", serverType.getIcon());
    myFactory = new DeployToServerConfigurationFactory();
    addFactory(myFactory);
    myServerType = serverType;
  }

  public ConfigurationFactoryEx getFactory() {
    return myFactory;
  }

  @NotNull
  public ServerType<?> getServerType() {
    return myServerType;
  }

  public class DeployToServerConfigurationFactory extends ConfigurationFactoryEx {
    public DeployToServerConfigurationFactory() {
      super(DeployToServerConfigurationType.this);
    }

    @Override
    public boolean isApplicable(@NotNull Project project) {
      return myServerType.canAutoDetectConfiguration() || !RemoteServersManager.getInstance().getServers(myServerType).isEmpty();
    }

    @Override
    public void onNewConfigurationCreated(@NotNull RunConfiguration configuration) {
      DeployToServerRunConfiguration<?, ?> deployConfiguration = (DeployToServerRunConfiguration<?, ?>)configuration;
      if (deployConfiguration.getServerName() == null) {
        RemoteServer<?> server = ContainerUtil.getFirstItem(RemoteServersManager.getInstance().getServers(myServerType));
        if (server != null) {
          deployConfiguration.setServerName(server.getName());
        }
      }

      if (deployConfiguration.getDeploymentSource() == null) {
        setupDeploymentSource(configuration, deployConfiguration);
      }
    }

    private <S extends ServerConfiguration, D extends DeploymentConfiguration> void setupDeploymentSource(
      @NotNull RunConfiguration configuration, @NotNull DeployToServerRunConfiguration<S, D> deployConfiguration) {

      DeploymentConfigurator<D, S> deploymentConfigurator = deployConfiguration.getDeploymentConfigurator();
      List<DeploymentSource> sources = deploymentConfigurator.getAvailableDeploymentSources();
      DeploymentSource source = ContainerUtil.getFirstItem(sources);
      if (source != null) {
        deployConfiguration.setDeploymentSource(source);
        deployConfiguration.setDeploymentConfiguration(deploymentConfigurator.createDefaultConfiguration(source));
        DeploymentSourceType type = source.getType();
        //noinspection unchecked
        type.setBuildBeforeRunTask(configuration, source);
      }
    }

    @Override
    @NotNull
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      DeploymentConfigurator<?, ?> deploymentConfigurator = myServerType.createDeploymentConfigurator(project);
      return new DeployToServerRunConfiguration(project, this, "", myServerType, deploymentConfigurator);
    }
  }
}
