// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurator;
import com.intellij.remoteServer.configuration.deployment.DeploymentSourceType;
import com.intellij.remoteServer.configuration.deployment.SingletonDeploymentSourceType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public final class DeployToServerConfigurationType extends ConfigurationTypeBase {
  private final ServerType<?> myServerType;
  private final MultiSourcesConfigurationFactory myMultiSourcesFactory;
  private final Map<SingletonDeploymentSourceType, SingletonTypeConfigurationFactory> myPerTypeFactories = new HashMap<>();

  public DeployToServerConfigurationType(@NotNull ServerType<?> serverType) {
    super(serverType.getId() + "-deploy", serverType.getDeploymentConfigurationTypePresentableName(),
          "Deploy to " + serverType.getPresentableName() + " run configuration", serverType.getIcon());

    myServerType = serverType;
    if (myServerType.mayHaveProjectSpecificDeploymentSources()) {
      myMultiSourcesFactory = new MultiSourcesConfigurationFactory();
      addFactory(myMultiSourcesFactory);
    }
    else {
      myMultiSourcesFactory = null;
    }

    for (SingletonDeploymentSourceType next : serverType.getSingletonDeploymentSourceTypes()) {
      SingletonTypeConfigurationFactory nextFactory = new SingletonTypeConfigurationFactory(next);
      addFactory(nextFactory);
      myPerTypeFactories.put(next, nextFactory);
    }
  }

  /**
   * @param sourceType hint for a type of deployment source or null if unknown
   */
  @NotNull
  public ConfigurationFactory getFactoryForType(@Nullable DeploymentSourceType<?> sourceType) {
    ConfigurationFactory result = null;
    if (sourceType instanceof SingletonDeploymentSourceType && myServerType.getSingletonDeploymentSourceTypes().contains(sourceType)) {
      result = myPerTypeFactories.get(sourceType);
    }
    if (result == null) {
      result = myMultiSourcesFactory;
    }
    assert result != null : "server type: " + myServerType + ", requested source type: " + sourceType;
    return result;
  }

  /**
   * Will be removed after 2017.3 (still cannot because Google Cloud Tools uses it)
   *
   * @deprecated use {@link #getFactoryForType(DeploymentSourceType)}
   */
  @Deprecated
  public ConfigurationFactoryEx getFactory() {
    return (ConfigurationFactoryEx)getFactoryForType(null);
  }

  @NotNull
  public ServerType<?> getServerType() {
    return myServerType;
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug." + myServerType.getId() + "-deploy";
  }

  // todo do not extends ConfigurationFactoryEx once Google Cloud Tools plugin will get rid of getFactory() usage
  public class DeployToServerConfigurationFactory extends ConfigurationFactoryEx<DeployToServerRunConfiguration<?, ?>> {
    public DeployToServerConfigurationFactory() {
      super(DeployToServerConfigurationType.this);
    }

    @Override
    public boolean isApplicable(@NotNull Project project) {
      return myServerType.canAutoDetectConfiguration() || !RemoteServersManager.getInstance().getServers(myServerType).isEmpty();
    }

    @Override
    @NotNull
    public DeployToServerRunConfiguration createTemplateConfiguration(@NotNull Project project) {
      DeploymentConfigurator<?, ?> deploymentConfigurator = myServerType.createDeploymentConfigurator(project);
      //noinspection unchecked
      return new DeployToServerRunConfiguration(project, this, "", myServerType, deploymentConfigurator);
    }
  }

  public final class MultiSourcesConfigurationFactory extends DeployToServerConfigurationFactory {
    @NotNull
    @Override
    public String getId() {
      //compatibility reasons, before 173 it was the only configuration factory stored with this ID
      return DeployToServerConfigurationType.this.getDisplayName();
    }
  }

  public final class SingletonTypeConfigurationFactory extends DeployToServerConfigurationFactory {
    private final SingletonDeploymentSourceType mySourceType;

    public SingletonTypeConfigurationFactory(@NotNull SingletonDeploymentSourceType sourceType) {
      mySourceType = sourceType;
    }

    @NotNull
    @Override
    public String getId() {
      return mySourceType.getId();
    }

    @NotNull
    @Nls
    @Override
    public String getName() {
      return mySourceType.getPresentableName();
    }

    @NotNull
    @Override
    public DeployToServerRunConfiguration createTemplateConfiguration(@NotNull Project project) {
      DeployToServerRunConfiguration result = super.createTemplateConfiguration(project);
      result.lockDeploymentSource(mySourceType);
      return result;
    }
  }
}

