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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.components.ComponentSerializationUtil;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.packaging.artifacts.ArtifactPointerManager;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.*;
import com.intellij.remoteServer.impl.runtime.DeployToServerState;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class DeployToServerRunConfiguration<S extends ServerConfiguration, D extends DeploymentConfiguration> extends RunConfigurationBase {
  @NonNls public static final String SETTINGS_ELEMENT = "settings";
  public static final SkipDefaultValuesSerializationFilters SERIALIZATION_FILTERS = new SkipDefaultValuesSerializationFilters();
  private final ServerType<S> myServerType;
  private final DeploymentConfigurator<D> myDeploymentConfigurator;
  private String myServerName;
  private DeploymentSource myDeploymentSource;
  private D myDeploymentConfiguration;

  public DeployToServerRunConfiguration(Project project, ConfigurationFactory factory, String name, ServerType<S> serverType, DeploymentConfigurator<D> deploymentConfigurator) {
    super(project, factory, name);
    myServerType = serverType;
    myDeploymentConfigurator = deploymentConfigurator;
  }

  public String getServerName() {
    return myServerName;
  }

  @NotNull
  public DeploymentConfigurator<D> getDeploymentConfigurator() {
    return myDeploymentConfigurator;
  }

  @NotNull
  @Override
  public SettingsEditor<DeployToServerRunConfiguration> getConfigurationEditor() {
    return new DeployToServerSettingsEditor(myServerType, myDeploymentConfigurator, getProject());
  }

  @Nullable
  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    String serverName = getServerName();
    if (serverName == null) {
      throw new ExecutionException("Server is not specified");
    }

    RemoteServer<S> server = RemoteServersManager.getInstance().findByName(serverName, myServerType);
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
  }

  public void setServerName(String serverName) {
    myServerName = serverName;
  }

  public DeploymentSource getDeploymentSource() {
    return myDeploymentSource;
  }

  public void setDeploymentSource(DeploymentSource deploymentSource) {
    myDeploymentSource = deploymentSource;
  }

  public D getDeploymentConfiguration() {
    return myDeploymentConfiguration;
  }

  public void setDeploymentConfiguration(D deploymentConfiguration) {
    myDeploymentConfiguration = deploymentConfiguration;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    ConfigurationState state = XmlSerializer.deserialize(element, ConfigurationState.class);
    myServerName =  null;
    myDeploymentSource = null;
    if (state != null) {
      myServerName = state.myServerName;
      if (!state.myDeploymentItemState.isEmpty()) {
        DeploymentItemState itemState = state.myDeploymentItemState.get(0);
        myDeploymentSource = itemState.createSource(getProject());
        myDeploymentConfiguration = myDeploymentConfigurator.createDefaultConfiguration(myDeploymentSource);
        if (itemState.mySettings != null) {
          ComponentSerializationUtil.loadComponentState(myDeploymentConfiguration.getSerializer(), itemState.mySettings);
        }
      }
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    ConfigurationState state = new ConfigurationState();
    state.myServerName = myServerName;
    if (myDeploymentSource != null) {
      DeploymentItemState itemState;
      if (myDeploymentSource instanceof ArtifactDeploymentSource) {
        itemState = new ArtifactDeploymentSettingsState(((ArtifactDeploymentSource)myDeploymentSource).getArtifactPointer().getArtifactName());
      }
      else if (myDeploymentSource instanceof ModuleDeploymentSource) {
        itemState = new ModuleDeploymentSettingsState(((ModuleDeploymentSource)myDeploymentSource).getModulePointer().getModuleName());
      }
      else {
        throw new WriteExternalException("Unknown source " + myDeploymentSource);
      }
      if (myDeploymentConfiguration != null) {
        itemState.setSettings(XmlSerializer.serialize(myDeploymentConfiguration.getSerializer().getState(), SERIALIZATION_FILTERS));
      }
      state.myDeploymentItemState.add(itemState);
    }
    XmlSerializer.serializeInto(state, element, SERIALIZATION_FILTERS);
    super.writeExternal(element);
  }

  public static class ConfigurationState {
    @Attribute("server-name")
    public String myServerName;

    //in fact this collection has no more than one element
    @Property(surroundWithTag = false)
    @AbstractCollection(surroundWithTag = false,
                        elementTypes = {ExternalFileDeploymentSettingsState.class, ArtifactDeploymentSettingsState.class, ModuleDeploymentSettingsState.class})
    public List<DeploymentItemState> myDeploymentItemState = new ArrayList<DeploymentItemState>();
  }

  public static abstract class DeploymentItemState {
    private Element mySettings;

    @Tag(SETTINGS_ELEMENT)
    public Element getSettings() {
      return mySettings;
    }

    public void setSettings(Element settings) {
      mySettings = settings;
    }

    @NotNull
    public abstract DeploymentSource createSource(Project project);
  }

  @Tag("file")
  public static class ExternalFileDeploymentSettingsState extends DeploymentItemState {
    @Attribute("path")
    public String myFilePath;

    public ExternalFileDeploymentSettingsState() {
    }

    public ExternalFileDeploymentSettingsState(String filePath) {
      myFilePath = filePath;
    }

    @NotNull
    @Override
    public DeploymentSource createSource(Project project) {
      throw new UnsupportedOperationException("'createSource' not implemented in " + getClass().getName());
    }
  }

  @Tag("artifact")
  public static class ArtifactDeploymentSettingsState extends DeploymentItemState {
    @Attribute("name")
    public String myArtifactName;

    public ArtifactDeploymentSettingsState() {
    }

    public ArtifactDeploymentSettingsState(String artifactName) {
      myArtifactName = artifactName;
    }

    @NotNull
    @Override
    public DeploymentSource createSource(Project project) {
      return new ArtifactDeploymentSourceImpl(ArtifactPointerManager.getInstance(project).createPointer(myArtifactName));
    }
  }

  @Tag("module")
  public static class ModuleDeploymentSettingsState extends DeploymentItemState {
    @Attribute("name")
    public String myModuleName;

    public ModuleDeploymentSettingsState() {
    }

    public ModuleDeploymentSettingsState(String artifactName) {
      myModuleName = artifactName;
    }

    @NotNull
    @Override
    public DeploymentSource createSource(Project project) {
      return new ModuleDeploymentSourceImpl(ModulePointerManager.getInstance(project).create(myModuleName));
    }
  }
}
