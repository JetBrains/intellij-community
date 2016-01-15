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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.remote.ext.CredentialsCase;
import com.intellij.remote.ext.CredentialsManager;
import com.intellij.remote.ext.RemoteCredentialsHandler;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
// TODO: (next) rename to 'RemoteSdkDataDelegate' ?
public class RemoteConnectionCredentialsWrapper {

  private UserDataHolderBase myCredentialsTypeHolder = new UserDataHolderBase();

  public <C> void setCredentials(Key<C> key, C credentials) {
    myCredentialsTypeHolder = new UserDataHolderBase();
    myCredentialsTypeHolder.putUserData(key, credentials);
  }

  public Object getConnectionKey() {
    return getCredentials();
  }

  public void save(final Element rootElement) {
    getTypeHandler().save(rootElement);
  }

  public static IllegalStateException unknownConnectionType() {
    return new IllegalStateException("Unknown connection type"); //TODO
  }

  public void copyTo(final RemoteConnectionCredentialsWrapper copy) {
    copy.myCredentialsTypeHolder = new UserDataHolderBase();

    Pair<Object, CredentialsType> credentialsAndProvider = getCredentialsAndType();

    credentialsAndProvider.getSecond().putCredentials(copy.myCredentialsTypeHolder, credentialsAndProvider.getFirst());
  }

  @NotNull
  public String getId() {
    return getTypeHandler().getId();
  }

  public RemoteCredentialsHandler getTypeHandler() {
    Pair<Object, CredentialsType> credentialsAndType = getCredentialsAndType();
    return credentialsAndType.getSecond().getHandler(credentialsAndType.getFirst());
  }

  public CredentialsType getRemoteConnectionType() {
    return getCredentialsAndType().getSecond();
  }

  public Object getCredentials() {
    return getCredentialsAndType().getFirst();
  }

  private Pair<Object, CredentialsType> getCredentialsAndType() {
    for (CredentialsType type : CredentialsManager.getInstance().getAllTypes()) {
      Object credentials = type.getCredentials(myCredentialsTypeHolder);
      if (credentials != null) {
        return Pair.create(credentials, type);
      }
    }
    throw unknownConnectionType();
  }

  public void switchType(CredentialsCase... cases) {
    Pair<Object, CredentialsType> credentialsAndType = getCredentialsAndType();
    CredentialsType type = credentialsAndType.getSecond();
    for (CredentialsCase credentialsCase : cases) {
      if (credentialsCase.getType() == type) {
        credentialsCase.process(credentialsAndType.getFirst());
        return;
      }
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof RemoteConnectionCredentialsWrapper) {
      RemoteConnectionCredentialsWrapper w = (RemoteConnectionCredentialsWrapper)obj;
      try {
        Object credentials = getCredentials();
        Object counterCredentials = w.getCredentials();
        return credentials.equals(counterCredentials);
      }
      catch (IllegalStateException e) {
        return false;
      }
    }
    return false;
  }

  public String getPresentableDetails(final String interpreterPath) {
    return getTypeHandler().getPresentableDetails(interpreterPath);
  }
}
