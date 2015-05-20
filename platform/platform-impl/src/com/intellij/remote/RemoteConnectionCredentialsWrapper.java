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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class RemoteConnectionCredentialsWrapper {
  public static final String VAGRANT_PREFIX = "vagrant://";
  public static final String SFTP_DEPLOYMENT_PREFIX = "sftp://";

  /**
   * Connection types
   */
  public final Key<VagrantBasedCredentialsHolder> VAGRANT_BASED_CREDENTIALS = Key.create("VAGRANT_BASED_CREDENTIALS");
  public final Key<WebDeploymentCredentialsHolder> WEB_DEPLOYMENT_BASED_CREDENTIALS = Key.create("WEB_DEPLOYMENT_BASED_CREDENTIALS");
  public final Key<RemoteCredentialsHolder> PLAIN_SSH_CREDENTIALS = Key.create("PLAIN_SSH_CREDENTIALS");

  private UserDataHolderBase myCredentialsTypeHolder = new UserDataHolderBase();

  public void setVagrantConnectionType(VagrantBasedCredentialsHolder vagrantBasedCredentials) {
    myCredentialsTypeHolder = new UserDataHolderBase();
    myCredentialsTypeHolder.putUserData(VAGRANT_BASED_CREDENTIALS, vagrantBasedCredentials);
  }


  private VagrantBasedCredentialsHolder getVagrantCredentials() {
    return myCredentialsTypeHolder.getUserData(VAGRANT_BASED_CREDENTIALS);
  }

  public void setPlainSshCredentials(RemoteCredentialsHolder credentials) {
    myCredentialsTypeHolder = new UserDataHolderBase();
    myCredentialsTypeHolder.putUserData(PLAIN_SSH_CREDENTIALS, credentials);
  }

  private RemoteCredentialsHolder getPlainSshCredentials() {
    return myCredentialsTypeHolder.getUserData(PLAIN_SSH_CREDENTIALS);
  }


  public void setWebDeploymentCredentials(WebDeploymentCredentialsHolder webDeploymentCredentials) {
    myCredentialsTypeHolder = new UserDataHolderBase();
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
      public void ssh(@NotNull RemoteCredentialsHolder cred) {
        cred.save(rootElement);
      }

      @Override
      public void vagrant(@NotNull VagrantBasedCredentialsHolder cred) {
        cred.save(rootElement);
      }

      @Override
      public void deployment(@NotNull WebDeploymentCredentialsHolder cred) {
        cred.save(rootElement);
      }
    });
  }

  public static IllegalStateException unknownConnectionType() {
    return new IllegalStateException("Unknown connection type"); //TODO
  }

  public void copyTo(final RemoteConnectionCredentialsWrapper copy) {
    copy.myCredentialsTypeHolder = new UserDataHolderBase();
    switchType(new RemoteSdkConnectionAcceptor() {
      @Override
      public void ssh(@NotNull RemoteCredentialsHolder cred) {
        copy.setPlainSshCredentials(getPlainSshCredentials());
      }

      @Override
      public void vagrant(@NotNull VagrantBasedCredentialsHolder cred) {
        copy.setVagrantConnectionType(getVagrantCredentials());
      }

      @Override
      public void deployment(@NotNull WebDeploymentCredentialsHolder cred) {
        copy.setWebDeploymentCredentials(getWebDeploymentCredentials());
      }
    });
  }

  @NotNull
  public String getId() {
    final Ref<String> result = Ref.create();
    switchType(new RemoteSdkConnectionAcceptor() {
      @Override
      public void ssh(@NotNull RemoteCredentialsHolder cred) {
        result.set(constructSshCredentialsFullPath(cred));
      }

      @Override
      public void vagrant(@NotNull VagrantBasedCredentialsHolder cred) {
        result.set(VAGRANT_PREFIX + cred.getVagrantFolder());
      }

      @Override
      public void deployment(@NotNull WebDeploymentCredentialsHolder cred) {
        result.set(constructSftpCredentialsFullPath(cred.getSshCredentials()));
      }
    });

    return result.get();
  }

  @NotNull
  private static String constructSftpCredentialsFullPath(RemoteCredentials cred) {
    return SFTP_DEPLOYMENT_PREFIX + cred.getUserName() + "@" + cred.getHost() + ":" + cred.getPort();
  }


  @NotNull
  public static String constructSshCredentialsFullPath(RemoteCredentials cred) {
    return RemoteCredentialsHolder.SSH_PREFIX + cred.getUserName() + "@" + cred.getHost() + ":" + cred.getPort();
  }

  public void switchType(@NotNull final RemoteSdkConnectionAcceptor acceptor) {
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

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof RemoteConnectionCredentialsWrapper) {
      RemoteConnectionCredentialsWrapper w = (RemoteConnectionCredentialsWrapper)obj;
      if (isVagrantConnection()) {
        return w.isVagrantConnection() && getVagrantCredentials().equals(w.getVagrantCredentials());
      }
      else if (isWebDeploymentConnection()) {
        return w.isWebDeploymentConnection() && getWebDeploymentCredentials().equals(w.getWebDeploymentCredentials());
      }
      else if (isPlainSshConnection()) {
        return w.isPlainSshConnection() && getPlainSshCredentials().equals(w.getPlainSshCredentials());
      }
    }
    return false;
  }

  public String getPresentableDetails(final String interpreterPath) {
    final Ref<String> result = Ref.create();
    switchType(new RemoteSdkConnectionAcceptor() {
      @Override
      public void ssh(@NotNull RemoteCredentialsHolder cred) {
        result.set("(" + constructSshCredentialsFullPath(cred) + interpreterPath + ")");
      }

      @Override
      public void vagrant(@NotNull VagrantBasedCredentialsHolder cred) {
        String pathRelativeToHome = FileUtil.getLocationRelativeToUserHome(cred.getVagrantFolder());

        result.set("Vagrant VM " +
                   (StringUtil.isNotEmpty(cred.getMachineName()) ? "'" + cred.getMachineName() + "' " : "") +
                   "at " + (pathRelativeToHome.length() < cred.getVagrantFolder().length() ? pathRelativeToHome : cred.getVagrantFolder())
                   + " (" + interpreterPath + ")");
      }

      @Override
      public void deployment(@NotNull WebDeploymentCredentialsHolder cred) {
        result.set("(" + constructSftpCredentialsFullPath(cred.getSshCredentials()) + interpreterPath + ")");
      }
    });

    return result.get();
  }
}
