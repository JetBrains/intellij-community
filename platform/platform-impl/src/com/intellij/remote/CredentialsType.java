// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.remote.ext.*;
import org.jetbrains.annotations.Nls;

/**
 * @author traff
 */
public abstract class CredentialsType<T> {

  public static final String VAGRANT_PREFIX = "vagrant://";
  public static final String SFTP_DEPLOYMENT_PREFIX = "sftp://";

  public static final Key<VagrantBasedCredentialsHolder> VAGRANT_BASED_CREDENTIALS = Key.create("VAGRANT_BASED_CREDENTIALS");
  public static final Key<WebDeploymentCredentialsHolder> WEB_DEPLOYMENT_BASED_CREDENTIALS = Key.create("WEB_DEPLOYMENT_BASED_CREDENTIALS");
  public static final Key<RemoteCredentialsHolder> PLAIN_SSH_CREDENTIALS = Key.create("PLAIN_SSH_CREDENTIALS");
  public static final Key<UnknownCredentialsHolder> UNKNOWN_CREDENTIALS = Key.create("UNKNOWN_CREDENTIALS");

  public static final CredentialsType<RemoteCredentialsHolder> SSH_HOST
    = new CredentialsType<RemoteCredentialsHolder>("SSH Credentials", RemoteCredentialsHolder.SSH_PREFIX) {

    @Override
    public Key<RemoteCredentialsHolder> getCredentialsKey() {
      return PLAIN_SSH_CREDENTIALS;
    }

    @Override
    public RemoteCredentialsHandler getHandler(RemoteCredentialsHolder credentials) {
      return new SshCredentialsHandler(credentials);
    }

    @Override
    public RemoteCredentialsHolder createCredentials() {
      return new RemoteCredentialsHolder();
    }
  };
  public static final CredentialsType<VagrantBasedCredentialsHolder> VAGRANT
    = new CredentialsType<VagrantBasedCredentialsHolder>("Vagrant", VAGRANT_PREFIX) {

    @Override
    public Key<VagrantBasedCredentialsHolder> getCredentialsKey() {
      return VAGRANT_BASED_CREDENTIALS;
    }

    @Override
    public RemoteCredentialsHandler getHandler(VagrantBasedCredentialsHolder credentials) {
      return new VagrantCredentialsHandler(credentials);
    }

    @Override
    public VagrantBasedCredentialsHolder createCredentials() {
      return new VagrantBasedCredentialsHolder();
    }
  };

  public static final CredentialsType<WebDeploymentCredentialsHolder> WEB_DEPLOYMENT
    = new CredentialsType<WebDeploymentCredentialsHolder>("Web Deployment", SFTP_DEPLOYMENT_PREFIX) {

    @Override
    public Key<WebDeploymentCredentialsHolder> getCredentialsKey() {
      return WEB_DEPLOYMENT_BASED_CREDENTIALS;
    }

    @Override
    public RemoteCredentialsHandler getHandler(WebDeploymentCredentialsHolder credentials) {
      return new WebDeploymentCredentialsHandler(credentials);
    }

    @Override
    public WebDeploymentCredentialsHolder createCredentials() {
      return new WebDeploymentCredentialsHolder();
    }
  };

  public static final CredentialsType<UnknownCredentialsHolder> UNKNOWN = new CredentialsType<UnknownCredentialsHolder>("Unknown", "") {
    @Override
    public Key<UnknownCredentialsHolder> getCredentialsKey() {
      return UNKNOWN_CREDENTIALS;
    }

    @Override
    public RemoteCredentialsHandler getHandler(UnknownCredentialsHolder credentials) {
      return new UnknownTypeRemoteCredentialHandler(credentials);
    }

    @Override
    public UnknownCredentialsHolder createCredentials() {
      return new UnknownCredentialsHolder();
    }
  };

  private final String myName;
  private final String myPrefix;

  protected CredentialsType(@Nls(capitalization = Nls.Capitalization.Title) String name, String prefix) {
    myName = name;
    myPrefix = prefix;
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

  public boolean hasPrefix(String path) {
    return path.startsWith(myPrefix);
  }

  public abstract T createCredentials();

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
