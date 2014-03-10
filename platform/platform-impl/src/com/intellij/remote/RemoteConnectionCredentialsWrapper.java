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
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class RemoteConnectionCredentialsWrapper {
  public static final String VAGRANT_PREFIX = "vagrant://";
  public static final String SFTP_DEPLOYMENT_PREFIX = "sftp://";

  public final Key<VagrantBasedCredentialsHolder> VAGRANT_BASED_CREDENTIALS = Key.create("VAGRANT_BASED_CREDENTIALS");
  public final Key<WebDeploymentCredentialsHolder> WEB_DEPLOYMENT_BASED_CREDENTIALS = Key.create("WEB_DEPLOYMENT_BASED_CREDENTIALS");

  public final Key<RemoteCredentialsHolder> PLAIN_SSH_CREDENTIALS = Key.create("PLAIN_SSH_CREDENTIALS");

  private UserDataHolderBase myCredentialsTypeHolder = new UserDataHolderBase();

  public void setVagrantConnectionType(VagrantBasedCredentialsHolder vagrantBasedCredentials) {
    myCredentialsTypeHolder.putUserData(VAGRANT_BASED_CREDENTIALS, vagrantBasedCredentials);
  }


  private VagrantBasedCredentialsHolder getVagrantCredentials() {
    return myCredentialsTypeHolder.getUserData(VAGRANT_BASED_CREDENTIALS);
  }

  public void setPlainSshCredentials(RemoteCredentialsHolder credentials) {
    myCredentialsTypeHolder.putUserData(PLAIN_SSH_CREDENTIALS, credentials);
  }

  private RemoteCredentialsHolder getPlainSshCredentials() {
    return myCredentialsTypeHolder.getUserData(PLAIN_SSH_CREDENTIALS);
  }


  public void setWebDeploymentCredentials(WebDeploymentCredentialsHolder webDeploymentCredentials) {
    myCredentialsTypeHolder.putUserData(WEB_DEPLOYMENT_BASED_CREDENTIALS, webDeploymentCredentials);
  }

  private WebDeploymentCredentialsHolder getWebDeploymentCredentials() {
    return myCredentialsTypeHolder.getUserData(WEB_DEPLOYMENT_BASED_CREDENTIALS);
  }

  private boolean isVagrantConnection() {
    return getVagrantCredentials() != null;
  }

  private boolean isPlainSshConnection() {
    return getPlainSshCredentials() != null;
  }

  private boolean isWebDeploymentConnection() {
    return getWebDeploymentCredentials() != null;
  }


  public Object getConnectionKey() {
    if (isVagrantConnection()) {
      return getVagrantCredentials();
    }
    else if (isPlainSshConnection()) {
      return getPlainSshCredentials();
    }
    else if (isWebDeploymentConnection()) {
      return getWebDeploymentCredentials();
    }
    else {
      throw unknownConnectionType();
    }
  }

  public void save(final Element rootElement) {
    switchType(new RemoteSdkConnectionAcceptor() {
      @Override
      public void ssh(RemoteCredentialsHolder cred) {
        cred.save(rootElement);
      }

      @Override
      public void vagrant(VagrantBasedCredentialsHolder cred) {
        cred.save(rootElement);
      }

      @Override
      public void deployment(WebDeploymentCredentialsHolder cred) {
        cred.save(rootElement);
      }
    });
  }

  public static IllegalStateException unknownConnectionType() {
    return new IllegalStateException("Unknown connection type"); //TODO
  }

  public void copyTo(RemoteConnectionCredentialsWrapper copy) {
    copy.myCredentialsTypeHolder = new UserDataHolderBase();
    copy.setPlainSshCredentials(getPlainSshCredentials());
    copy.setVagrantConnectionType(getVagrantCredentials());
    copy.setWebDeploymentCredentials(getWebDeploymentCredentials());
  }

  public String getId() {
    if (isVagrantConnection()) {
      @NotNull VagrantBasedCredentialsHolder cred = getVagrantCredentials();

      return VAGRANT_PREFIX + cred.getVagrantFolder();
    }
    else if (isPlainSshConnection()) {
      RemoteCredentials cred = getPlainSshCredentials();

      return constructSshCredentialsFullPath(cred);
    }
    else if (isWebDeploymentConnection()) {
      WebDeploymentCredentialsHolder cred = getWebDeploymentCredentials();
      return constructSftpCredentialsFullPath(cred.getSshCredentials());
    }
    else {
      throw unknownConnectionType();
    }
  }

  private static String constructSftpCredentialsFullPath(RemoteCredentials cred) {
    return SFTP_DEPLOYMENT_PREFIX + cred.getUserName() + "@" + cred.getHost() + ":" + cred.getPort();
  }


  public static String constructSshCredentialsFullPath(RemoteCredentials cred) {
    return RemoteSdkCredentialsHolder.SSH_PREFIX + cred.getUserName() + "@" + cred.getHost() + ":" + cred.getPort();
  }

  public void switchType(RemoteSdkConnectionAcceptor acceptor) {
    if (isVagrantConnection()) {
      acceptor.vagrant(getVagrantCredentials());
    }
    else if (isPlainSshConnection()) {
      acceptor.ssh(getPlainSshCredentials());
    }
    else if (isWebDeploymentConnection()) {
      acceptor.deployment(getWebDeploymentCredentials());
    }
    else {
      throw unknownConnectionType();
    }
  }
}
