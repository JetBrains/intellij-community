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
package com.intellij.remoteServer.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.deployment.Deployer;
import com.intellij.remoteServer.deployment.DeploymentSource;
import com.intellij.remoteServer.deployment.DeploymentSourceUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class DeployToServerRunConfiguration<C extends ServerConfiguration> extends RunConfigurationBase {
  public static final String SERVER_NAME_ATTRIBUTE = "server-name";
  public static final String SOURCE_ELEMENT = "source";
  private final ServerType<C> myServerType;
  private final Deployer<C> myDeployer;
  private String myServerName;
  private DeploymentSource myDeploymentSource;

  public DeployToServerRunConfiguration(Project project, ConfigurationFactory factory, String name, ServerType<C> serverType) {
    super(project, factory, name);
    myServerType = serverType;
    myDeployer = myServerType.createDeployer(project);
  }

  public String getServerName() {
    return myServerName;
  }

  @NotNull
  public Deployer getDeployer() {
    return myDeployer;
  }

  @NotNull
  @Override
  public SettingsEditor<DeployToServerRunConfiguration> getConfigurationEditor() {
    return new DeployToServerSettingsEditor(myServerType, myDeployer, getProject());
  }

  @Nullable
  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    String serverName = getServerName();
    if (serverName == null) {
      throw new ExecutionException("Server is not specified");
    }

    RemoteServer<C> server = RemoteServersManager.getInstance().findByName(serverName, myServerType);
    if (server == null) {
      throw new ExecutionException("Server '" + serverName + " not found");
    }

    if (myDeploymentSource == null) {
      throw new ExecutionException("Deployment is not selected");
    }

    return new DeployToServerState(myDeployer, server, myDeploymentSource);
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

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myServerName = element.getAttributeValue(SERVER_NAME_ATTRIBUTE);
    Element sourceElement = element.getChild(SOURCE_ELEMENT);
    myDeploymentSource = sourceElement != null ? DeploymentSourceUtil.getInstance().loadDeploymentSource(sourceElement, getProject()) : null;
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    if (myServerName != null) {
      element.setAttribute(SERVER_NAME_ATTRIBUTE, myServerName);
    }
    if (myDeploymentSource != null) {
      Element source = new Element(SOURCE_ELEMENT);
      DeploymentSourceUtil.getInstance().saveDeploymentSource(myDeploymentSource, source, getProject());
      element.addContent(source);
    }
    super.writeExternal(element);
  }
}
