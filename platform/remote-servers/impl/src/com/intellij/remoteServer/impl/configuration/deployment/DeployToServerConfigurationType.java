// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.remoteServer.CloudBundle;
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

public final class DeployToServerConfigurationType extends ConfigurationTypeBase {
  private final String myServerTypeId;
  private final MultiSourcesConfigurationFactory myMultiSourcesFactory;
  private final Map<String, SingletonTypeConfigurationFactory> myPerTypeFactories = new HashMap<>();

  public DeployToServerConfigurationType(@NotNull ServerType<?> serverType) {
    super(serverType.getId() + "-deploy", serverType.getDeploymentConfigurationTypePresentableName(),
          CloudBundle.message("deploy.to.server.configuration.type.description", serverType.getPresentableName()),
          NotNullLazyValue.lazy(serverType::getIcon));

    myServerTypeId = serverType.getId();
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

  boolean isForServerType(String serverTypeId) {
    return serverTypeId.equals(myServerTypeId);
  }

  /**
   * @param sourceType hint for a type of deployment source or null if unknown
   */
  @NotNull
  public ConfigurationFactory getFactoryForType(@Nullable DeploymentSourceType<?> sourceType) {
    ConfigurationFactory result = null;
    if (sourceType instanceof SingletonDeploymentSourceType && getServerType().getSingletonDeploymentSourceTypes().contains(sourceType)) {
      result = myPerTypeFactories.get(sourceType.getId());
    }
    if (result == null) {
      result = myMultiSourcesFactory;
    }
    assert result != null : "server type: " + myServerTypeId + ", requested source type: " + sourceType;
    return result;
  }

  @NotNull
  public ServerType<?> getServerType() {
    ServerType<?> result = ServerType.EP_NAME.findFirstSafe(next -> myServerTypeId.equals(next.getId()));
    assert result != null : "Sever type " + myServerTypeId + " had been unloaded already";
    return result;
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug." + myServerTypeId + "-deploy";
  }

  // todo do not extends ConfigurationFactoryEx once Google Cloud Tools plugin will get rid of getFactory() usage
  public abstract class DeployToServerConfigurationFactory extends ConfigurationFactoryEx<DeployToServerRunConfiguration<?, ?>> {
    public DeployToServerConfigurationFactory() {
      super(DeployToServerConfigurationType.this);
    }

    @Override
    public boolean isApplicable(@NotNull Project project) {
      ServerType<?> serverType = getServerType();
      return serverType.canAutoDetectConfiguration() || !RemoteServersManager.getInstance().getServers(serverType).isEmpty();
    }

    @Override
    @NotNull
    public DeployToServerRunConfiguration createTemplateConfiguration(@NotNull Project project) {
      ServerType<?> serverType = getServerType();
      DeploymentConfigurator<?, ?> deploymentConfigurator = serverType.createDeploymentConfigurator(project);
      //noinspection unchecked
      return new DeployToServerRunConfiguration(project, this, "", serverType, deploymentConfigurator);
    }
  }

  public final class MultiSourcesConfigurationFactory extends DeployToServerConfigurationFactory {
    @NotNull
    @Override
    public String getId() {
      //compatibility reasons, before 173 it was the only configuration factory stored with this ID
      return DeployToServerConfigurationType.this.getServerType().getDeploymentConfigurationFactoryId();
    }
  }

  public final class SingletonTypeConfigurationFactory extends DeployToServerConfigurationFactory {
    private final String mySourceTypeId;
    private final @Nls String myPresentableName;

    public SingletonTypeConfigurationFactory(@NotNull SingletonDeploymentSourceType sourceType) {
      mySourceTypeId = sourceType.getId();
      myPresentableName = sourceType.getPresentableName();
    }

    @NotNull
    @Override
    public String getId() {
      return mySourceTypeId;
    }

    @NotNull
    @Nls
    @Override
    public String getName() {
      return myPresentableName;
    }

    @NotNull
    @Override
    public DeployToServerRunConfiguration createTemplateConfiguration(@NotNull Project project) {
      DeployToServerRunConfiguration result = super.createTemplateConfiguration(project);
      DeploymentSourceType<?> type = getSourceTypeImpl();
      if (type instanceof SingletonDeploymentSourceType) {
        result.lockDeploymentSource((SingletonDeploymentSourceType)type);
      }
      return result;
    }

    @Nullable
    private DeploymentSourceType<?> getSourceTypeImpl() {
      return DeploymentSourceType.EP_NAME.findFirstSafe(next -> mySourceTypeId.equals(next.getId()));
    }

    @Override
    public boolean isEditableInDumbMode() {
      return getSourceTypeImpl().isEditableInDumbMode();
    }
  }
}

