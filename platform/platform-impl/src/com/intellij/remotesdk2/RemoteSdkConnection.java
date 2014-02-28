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
package com.intellij.remotesdk2;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.remotesdk.RemoteSdkCredentials;
import com.intellij.remotesdk.RemoteSdkCredentialsHolder;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class RemoteSdkConnection<T extends RemoteSdkCredentialsHolder> {
  public static final String VAGRANT_PREFIX = "vagrant://";

  public final Key<VagrantBasedCredentialsHolder> VAGRANT_BASED_CREDENTIALS = Key.create("VAGRANT_BASED_CREDENTIALS");

  public final Key<T> PLAIN_SSH_CREDENTIALS = Key.create("PLAIN_SSH_CREDENTIALS");

  private UserDataHolderBase myCredentialsTypeHolder = new UserDataHolderBase();

  public void setVagrantConnectionType(VagrantBasedCredentialsHolder vagrantBasedCredentials) {
    myCredentialsTypeHolder.putUserData(VAGRANT_BASED_CREDENTIALS, vagrantBasedCredentials);
  }

  public void setPlainSshCredentials(T credentials) {
    myCredentialsTypeHolder.putUserData(PLAIN_SSH_CREDENTIALS, credentials);
  }

  public T getPlainSshCredentials() {
    return myCredentialsTypeHolder.getUserData(PLAIN_SSH_CREDENTIALS);
  }

  public VagrantBasedCredentialsHolder getVagrantCredentials() {
    return myCredentialsTypeHolder.getUserData(VAGRANT_BASED_CREDENTIALS);
  }

  public boolean isVagrantConnection() {
    return getVagrantCredentials() != null;
  }

  public boolean isPlainSshConnection() {
    return getPlainSshCredentials() != null;
  }


  public Object getConnectionKey() {
    if (isVagrantConnection()) {
      return getVagrantCredentials();
    }
    else if (isPlainSshConnection()) {
      return getPlainSshCredentials();
    }
    else {
      throw unknownConnectionType();
    }
  }

  public void save(Element rootElement) {
    if (isVagrantConnection()) {
      getVagrantCredentials().save(rootElement);
    }
    else if (isPlainSshConnection()) {
      getPlainSshCredentials().save(rootElement);
    }
    else {
      throw unknownConnectionType();
    }
  }

  private static IllegalStateException unknownConnectionType() {
    return new IllegalStateException(); //TODO
  }

  public void copyTo(RemoteSdkConnection<T> copy) {
    copy.myCredentialsTypeHolder = new UserDataHolderBase();
    copy.setPlainSshCredentials(getPlainSshCredentials());
    copy.setVagrantConnectionType(getVagrantCredentials());
  }

  public String getId() {
    if (isVagrantConnection()) {
      @NotNull VagrantBasedCredentialsHolder cred = getVagrantCredentials();

      return VAGRANT_PREFIX + cred.getVagrantFolder();
    }
    else if (isPlainSshConnection()) {
      RemoteSdkCredentials cred = getPlainSshCredentials();

      return RemoteSdkCredentialsHolder.constructSshCredentialsSdkFullPath(cred);
    }
    else {
      throw unknownConnectionType();
    }
  }
}
