// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.CloudBundle;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurator;
import com.intellij.remoteServer.configuration.deployment.DeploymentSourceType;
import com.intellij.remoteServer.configuration.deployment.SingletonDeploymentSourceType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public final class DeployToServerConfigurationType<C extends ServerConfiguration> extends ConfigurationTypeBase {

  private final @NotNull ServerType<C> myServerType;
  private final @Nullable MultiSourcesConfigurationFactory myMultiSourcesFactory;
  private final Map<String, SingletonTypeConfigurationFactory> myPerTypeFactories = new HashMap<>();

  public DeployToServerConfigurationType(@NotNull ServerType<C> serverType) {
    super(serverType.getId() + "-deploy",
          serverType.getDeploymentConfigurationTypePresentableName(),
          CloudBundle.message("deploy.to.server.configuration.type.description", serverType.getPresentableName()),
          (Icon)null);

    myServerType = serverType;
    if (serverType.mayHaveProjectSpecificDeploymentSources()) {
      myMultiSourcesFactory = new MultiSourcesConfigurationFactory();
      addFactory(myMultiSourcesFactory);
    }
    else {
      myMultiSourcesFactory = null;
    }

    for (SingletonDeploymentSourceType next : serverType.getSingletonDeploymentSourceTypes()) {
      SingletonTypeConfigurationFactory nextFactory = new SingletonTypeConfigurationFactory(next);
      addFactory(nextFactory);
      myPerTypeFactories.put(next.getId(), nextFactory);
    }
  }

  /**
   * @param sourceType hint for a type of deployment source or null if unknown
   */
  public @NotNull ConfigurationFactory getFactoryForType(@Nullable DeploymentSourceType<?> sourceType) {
    ConfigurationFactory result = null;
    if (sourceType instanceof SingletonDeploymentSourceType &&
        myServerType.getSingletonDeploymentSourceTypes().contains(sourceType)) {
      result = myPerTypeFactories.get(sourceType.getId());
    }
    if (result == null) {
      result = myMultiSourcesFactory;
    }
    assert result != null : "server type: " + myServerType.getId() + ", requested source type: " + sourceType;
    return result;
  }

  @Override
  public @NotNull Icon getIcon() {
    return myServerType.getIcon();
  }

  @Override
  public @NotNull @NonNls String getHelpTopic() {
    return "reference.dialogs.rundebug." + getId();
  }

  // todo do not extends ConfigurationFactoryEx once Google Cloud Tools plugin will get rid of getFactory() usage
  public abstract class DeployToServerConfigurationFactory extends ConfigurationFactoryEx<DeployToServerRunConfiguration<C, ?>> {

    public DeployToServerConfigurationFactory() {
      super(DeployToServerConfigurationType.this);
    }

    @Override
    public boolean isApplicable(@NotNull Project project) {
      return myServerType.canAutoDetectConfiguration() ||
             !RemoteServersManager.getInstance().getServers(myServerType).isEmpty();
    }

    @Override
    public @NotNull DeployToServerRunConfiguration<C, ?> createTemplateConfiguration(@NotNull Project project) {
      DeploymentConfigurator<?, C> deploymentConfigurator = myServerType.createDeploymentConfigurator(project);
      return new DeployToServerRunConfiguration<>(project, this, "", myServerType, deploymentConfigurator);
    }
  }

  public final class MultiSourcesConfigurationFactory extends DeployToServerConfigurationFactory {

    @Override
    public @NotNull @NonNls String getId() {
      //compatibility reasons, before 173 it was the only configuration factory stored with this ID
      return myServerType.getDeploymentConfigurationFactoryId();
    }
  }

  public final class SingletonTypeConfigurationFactory extends DeployToServerConfigurationFactory {

    private final @NotNull @NonNls String mySourceTypeId;
    private final @NotNull @Nls String myPresentableName;

    public SingletonTypeConfigurationFactory(@NotNull SingletonDeploymentSourceType sourceType) {
      mySourceTypeId = sourceType.getId();
      myPresentableName = sourceType.getPresentableName();
    }

    @Override
    public @NotNull @NonNls String getId() {
      return mySourceTypeId;
    }

    @Override
    public @NotNull @Nls String getName() {
      return myPresentableName;
    }

    @Override
    public @NotNull DeployToServerRunConfiguration<C, ?> createTemplateConfiguration(@NotNull Project project) {
      DeployToServerRunConfiguration<C, ?> result = super.createTemplateConfiguration(project);
      DeploymentSourceType<?> type = getSourceTypeImpl();
      if (type instanceof SingletonDeploymentSourceType) {
        result.lockDeploymentSource((SingletonDeploymentSourceType)type);
      }
      return result;
    }

    private @Nullable DeploymentSourceType<?> getSourceTypeImpl() {
      return DeploymentSourceType.EP_NAME.findFirstSafe(next -> mySourceTypeId.equals(next.getId()));
    }

    @Override
    public boolean isEditableInDumbMode() {
      return getSourceTypeImpl().isEditableInDumbMode();
    }
  }
}

