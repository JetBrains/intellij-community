/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.remote;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.remote.ext.*;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @author traff
 */
public abstract class CredentialsType<T> {

  public static final String VAGRANT_PREFIX = "vagrant://";
  public static final String SFTP_DEPLOYMENT_PREFIX = "sftp://";
  public static final String DOCKER_PREFIX = "docker://";


  public static final Key<VagrantBasedCredentialsHolder> VAGRANT_BASED_CREDENTIALS = Key.create("VAGRANT_BASED_CREDENTIALS");
  public static final Key<WebDeploymentCredentialsHolder> WEB_DEPLOYMENT_BASED_CREDENTIALS = Key.create("WEB_DEPLOYMENT_BASED_CREDENTIALS");
  public static final Key<RemoteCredentialsHolder> PLAIN_SSH_CREDENTIALS = Key.create("PLAIN_SSH_CREDENTIALS");
  public static final Key<DockerCredentialsHolder> DOCKER_CREDENTIALS = Key.create("DOCKER_CREDENTIALS");

  public static final CredentialsType<RemoteCredentialsHolder> SSH_HOST
    = new CredentialsType<RemoteCredentialsHolder>("SSH Credentials") {

    @Override
    public Key<RemoteCredentialsHolder> getCredentialsKey() {
      return PLAIN_SSH_CREDENTIALS;
    }

    @Override
    public RemoteCredentialsHandler getHandler(RemoteCredentialsHolder credentials) {
      return new SshCredentialsHandler(credentials);
    }

    @Override
    public String getPrefix() {
      return RemoteCredentialsHolder.SSH_PREFIX;
    }

    @Override
    public RemoteCredentialsHolder createCredentials() {
      return new RemoteCredentialsHolder();
    }
  };
  public static final CredentialsType<VagrantBasedCredentialsHolder> VAGRANT
    = new CredentialsType<VagrantBasedCredentialsHolder>("Vagrant") {

    @Override
    public Key<VagrantBasedCredentialsHolder> getCredentialsKey() {
      return VAGRANT_BASED_CREDENTIALS;
    }

    @Override
    public RemoteCredentialsHandler getHandler(VagrantBasedCredentialsHolder credentials) {
      return new VagrantCredentialsHandler(credentials);
    }

    @Override
    public String getPrefix() {
      return VAGRANT_PREFIX;
    }

    @Override
    public VagrantBasedCredentialsHolder createCredentials() {
      return new VagrantBasedCredentialsHolder();
    }
  };

  public static final CredentialsType<WebDeploymentCredentialsHolder> WEB_DEPLOYMENT
    = new CredentialsType<WebDeploymentCredentialsHolder>("Web Deployment") {

    @Override
    public Key<WebDeploymentCredentialsHolder> getCredentialsKey() {
      return WEB_DEPLOYMENT_BASED_CREDENTIALS;
    }

    @Override
    public RemoteCredentialsHandler getHandler(WebDeploymentCredentialsHolder credentials) {
      return new WebDeploymentCredentialsHandler(credentials);
    }

    @Override
    public String getPrefix() {
      return SFTP_DEPLOYMENT_PREFIX;
    }

    @Override
    public WebDeploymentCredentialsHolder createCredentials() {
      return new WebDeploymentCredentialsHolder();
    }
  };

  // TODO: contribute
  public static final CredentialsType<DockerCredentialsHolder> DOCKER
    = new CredentialsType<DockerCredentialsHolder>("Docker") {

    @Override
    public Key<DockerCredentialsHolder> getCredentialsKey() {
      return DOCKER_CREDENTIALS;
    }

    @Override
    public RemoteCredentialsHandler getHandler(DockerCredentialsHolder credentials) {
      return new DockerCredentialsHandler(credentials);
    }

    @Override
    public String getPrefix() {
      return DOCKER_PREFIX;
    }

    @Override
    public DockerCredentialsHolder createCredentials() {
      return new DockerCredentialsHolder();
    }
  };

  public static final List<CredentialsType> TYPES = Arrays.<CredentialsType>asList(
    SSH_HOST,
    VAGRANT,
    WEB_DEPLOYMENT,
    DOCKER);


  private final String myName;

  protected CredentialsType(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }

  public T getCredentials(UserDataHolderBase dataHolder) {
    return dataHolder.getUserData(getCredentialsKey());
  }

  public void putCredentials(UserDataHolderBase dataHolder, T credentials) {
    dataHolder.putUserData(getCredentialsKey(), credentials);
  }

  public abstract Key<T> getCredentialsKey();

  public abstract RemoteCredentialsHandler getHandler(T credentials);

  public abstract String getPrefix();

  public abstract T createCredentials();

  public static void loadCredentials(String interpreterPath, @Nullable Element element, RemoteSdkAdditionalData data) {
    for (CredentialsType type : TYPES) {
      if (interpreterPath.startsWith(type.getPrefix())) {
        Object credentials = type.createCredentials();
        type.getHandler(credentials).load(element);
        data.setCredentials(type.getCredentialsKey(), credentials);
      }
    }
  }

  public void saveCredentials(RemoteSdkAdditionalData data, CredentialsCase... cases) {
    for (CredentialsCase credentialsCase : cases) {
      if (credentialsCase.getType() == this) {
        T credentials = createCredentials();
        credentialsCase.process(credentials);
        data.setCredentials(getCredentialsKey(), credentials);
      }
    }
  }
}
