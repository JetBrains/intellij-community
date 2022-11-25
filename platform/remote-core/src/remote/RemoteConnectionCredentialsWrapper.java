// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.remote.ext.CredentialsCase;
import com.intellij.remote.ext.CredentialsManager;
import com.intellij.remote.ext.RemoteCredentialsHandler;
import com.intellij.remote.ext.UnknownCredentialsHolder;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public @NotNull String getId() {
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
    for (CredentialsType<?> type : CredentialsManager.getInstance().getAllTypes()) {
      Object credentials = getCredentials(type);
      if (credentials != null) {
        return Pair.create(credentials, type);
      }
    }
    final UnknownCredentialsHolder credentials = CredentialsType.UNKNOWN.getCredentials(myCredentialsTypeHolder);
    if (credentials != null) return Pair.create(credentials, CredentialsType.UNKNOWN);
    throw unknownConnectionType();
  }

  public <T> @Nullable T getCredentials(@NotNull CredentialsType<T> credentialsType) {
    return credentialsType.getCredentials(myCredentialsTypeHolder);
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
