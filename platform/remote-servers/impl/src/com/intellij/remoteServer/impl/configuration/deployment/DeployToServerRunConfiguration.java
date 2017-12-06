/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.configurationStore.ComponentSerializationUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.*;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerSettingsEditor.AnySource;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerSettingsEditor.LockedSource;
import com.intellij.remoteServer.impl.runtime.DeployToServerState;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class DeployToServerRunConfiguration<S extends ServerConfiguration, D extends DeploymentConfiguration> extends RunConfigurationBase
  implements LocatableConfiguration {
  private static final Logger LOG = Logger.getInstance(DeployToServerRunConfiguration.class);
  private static final String DEPLOYMENT_SOURCE_TYPE_ATTRIBUTE = "type";
  @NonNls public static final String SETTINGS_ELEMENT = "settings";
  public static final SkipDefaultValuesSerializationFilters SERIALIZATION_FILTERS = new SkipDefaultValuesSerializationFilters();
  private final ServerType<S> myServerType;
  private final DeploymentConfigurator<D, S> myDeploymentConfigurator;
  private String myServerName;
  private boolean myDeploymentSourceIsLocked;
  private DeploymentSource myDeploymentSource;
  private D myDeploymentConfiguration;

  public DeployToServerRunConfiguration(Project project,
                                        ConfigurationFactory factory,
                                        String name,
                                        ServerType<S> serverType,
                                        DeploymentConfigurator<D, S> deploymentConfigurator) {
    super(project, factory, name);
    myServerType = serverType;
    myDeploymentConfigurator = deploymentConfigurator;
  }

  void lockDeploymentSource(@NotNull SingletonDeploymentSourceType theOnlySourceType) {
    myDeploymentSourceIsLocked = true;
    myDeploymentSource = theOnlySourceType.getSingletonSource();
  }

  @NotNull
  public ServerType<S> getServerType() {
    return myServerType;
  }

  public String getServerName() {
    return myServerName;
  }

  @NotNull
  public DeploymentConfigurator<D, S> getDeploymentConfigurator() {
    return myDeploymentConfigurator;
  }

  @NotNull
  @Override
  public SettingsEditor<DeployToServerRunConfiguration> getConfigurationEditor() {
    //noinspection unchecked
    SettingsEditor<DeployToServerRunConfiguration> commonEditor =
      myDeploymentSourceIsLocked ? new LockedSource(myServerType, myDeploymentConfigurator, getProject(), myDeploymentSource)
                                 : new AnySource(myServerType, myDeploymentConfigurator, getProject());


    SettingsEditorGroup<DeployToServerRunConfiguration> group = new SettingsEditorGroup<>();
    group.addEditor("Deployment", commonEditor);
    DeployToServerRunConfigurationExtensionsManager.getInstance().appendEditors(this, group);
    return group;
  }

  @Nullable
  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    String serverName = getServerName();
    if (serverName == null) {
      throw new ExecutionException("Server is not specified");
    }

    RemoteServer<S> server = findServer();
    if (server == null) {
      throw new ExecutionException("Server '" + serverName + " not found");
    }

    if (myDeploymentSource == null) {
      throw new ExecutionException("Deployment is not selected");
    }

    return new DeployToServerState(server, myDeploymentSource, myDeploymentConfiguration, env);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    RemoteServer<S> server = findServer();
    if (server == null) {
      return;
    }

    if (myDeploymentSource == null) {
      return;
    }

    myDeploymentConfiguration.checkConfiguration(server, myDeploymentSource, getProject());
  }

  private RemoteServer<S> findServer() {
    String serverName = getServerName();
    if (serverName == null) {
      return null;
    }

    return RemoteServersManager.getInstance().findByName(serverName, myServerType);
  }

  public void setServerName(String serverName) {
    myServerName = serverName;
  }

  public DeploymentSource getDeploymentSource() {
    return myDeploymentSource;
  }

  public void setDeploymentSource(DeploymentSource deploymentSource) {
    if (myDeploymentSourceIsLocked) {
      assert deploymentSource != null && deploymentSource == myDeploymentSource
        : "Can't replace locked " + myDeploymentSource + " with " + deploymentSource;
    }
    myDeploymentSource = deploymentSource;
  }

  public D getDeploymentConfiguration() {
    return myDeploymentConfiguration;
  }

  public void setDeploymentConfiguration(D deploymentConfiguration) {
    myDeploymentConfiguration = deploymentConfiguration;
  }

  @Override
  public boolean isGeneratedName() {
    return getDeploymentSource() != null && getDeploymentConfiguration() != null &&
           getDeploymentConfigurator().isGeneratedConfigurationName(getName(), getDeploymentSource(), getDeploymentConfiguration());
  }

  @Nullable
  @Override
  public String suggestedName() {
    if (getDeploymentSource() == null || getDeploymentConfiguration() == null) {
      return null;
    }
    return getDeploymentConfigurator().suggestConfigurationName(getDeploymentSource(), getDeploymentConfiguration());
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    ConfigurationState state = XmlSerializer.deserialize(element, ConfigurationState.class);
    myServerName = null;
    myDeploymentSource = null;
    myServerName = state.myServerName;
    final Element deploymentTag = state.myDeploymentTag;
    if (deploymentTag != null) {
      String typeId = deploymentTag.getAttributeValue(DEPLOYMENT_SOURCE_TYPE_ATTRIBUTE);
      final DeploymentSourceType<?> type = findDeploymentSourceType(typeId);
      if (type != null) {
        myDeploymentSource = new ReadAction<DeploymentSource>() {
          @Override
          protected void run(final @NotNull Result<DeploymentSource> result) {
            result.setResult(type.load(deploymentTag, getProject()));
          }
        }.execute().getResultObject();
        myDeploymentConfiguration = myDeploymentConfigurator.createDefaultConfiguration(myDeploymentSource);
        ComponentSerializationUtil.loadComponentState(myDeploymentConfiguration.getSerializer(), deploymentTag.getChild(SETTINGS_ELEMENT));
      }
      else {
        LOG.warn("Cannot load deployment source for '" + getName() + "' run configuration: unknown deployment type '" + typeId + "'");
      }
    }
  }

  @Nullable
  private static DeploymentSourceType<?> findDeploymentSourceType(@Nullable String id) {
    for (DeploymentSourceType<?> type : DeploymentSourceType.EP_NAME.getExtensions()) {
      if (type.getId().equals(id)) {
        return type;
      }
    }
    return null;
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    ConfigurationState state = new ConfigurationState();
    state.myServerName = myServerName;
    if (myDeploymentSource != null) {
      DeploymentSourceType type = myDeploymentSource.getType();
      Element deploymentTag = new Element("deployment").setAttribute(DEPLOYMENT_SOURCE_TYPE_ATTRIBUTE, type.getId());
      type.save(myDeploymentSource, deploymentTag);
      if (myDeploymentConfiguration != null) {
        Object configurationState = myDeploymentConfiguration.getSerializer().getState();
        if (configurationState != null) {
          Element settingsTag = new Element(SETTINGS_ELEMENT);
          XmlSerializer.serializeInto(configurationState, settingsTag, SERIALIZATION_FILTERS);
          deploymentTag.addContent(settingsTag);
        }
      }
      state.myDeploymentTag = deploymentTag;
    }
    XmlSerializer.serializeInto(state, element, SERIALIZATION_FILTERS);
    super.writeExternal(element);
  }

  @Override
  public RunConfiguration clone() {
    Element element = new Element("tag");
    try {
      writeExternal(element);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }

    DeployToServerRunConfiguration result = (DeployToServerRunConfiguration)super.clone();
    if (myDeploymentSourceIsLocked) {
      result.lockDeploymentSource((SingletonDeploymentSourceType)myDeploymentSource.getType());
    }

    try {
      result.readExternal(element);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
    return result;
  }

  public static class ConfigurationState {
    @Attribute("server-name")
    public String myServerName;

    @Tag("deployment")
    public Element myDeploymentTag;
  }
}
