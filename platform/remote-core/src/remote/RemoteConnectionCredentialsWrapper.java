// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.remote.ext.CredentialsCase;
import com.intellij.remote.ext.CredentialsManager;
import com.intellij.remote.ext.RemoteCredentialsHandler;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoteConnectionCredentialsWrapper {
  private UserDataHolderBase myCredentialsTypeHolder = new UserDataHolderBase();

  public <C> void setCredentials(Key<C> key, C credentials) {
    myCredentialsTypeHolder = new UserDataHolderBase();
    myCredentialsTypeHolder.putUserData(key, credentials);
  }

  public Object getConnectionKey() {
    return getCredentials();
  }

  public void save(Element rootElement) {
    getTypeHandler().save(rootElement);
  }

  @SuppressWarnings("unchecked")
  public void copyTo(RemoteConnectionCredentialsWrapper copy) {
    copy.myCredentialsTypeHolder = new UserDataHolderBase();
    var credentialsAndProvider = getCredentialsAndType();
    credentialsAndProvider.second.putCredentials(copy.myCredentialsTypeHolder, credentialsAndProvider.first);
  }

  public @NotNull String getId() {
    return getTypeHandler().getId();
  }

  @SuppressWarnings("unchecked")
  public RemoteCredentialsHandler getTypeHandler() {
    var credentialsAndType = getCredentialsAndType();
    return credentialsAndType.second.getHandler(credentialsAndType.first);
  }

  @SuppressWarnings("rawtypes")
  public CredentialsType getRemoteConnectionType() {
    return getCredentialsAndType().second;
  }

  public Object getCredentials() {
    return getCredentialsAndType().first;
  }

  @SuppressWarnings("rawtypes")
  private Pair<Object, CredentialsType> getCredentialsAndType() {
    for (var type : CredentialsManager.getInstance().getAllTypes()) {
      var credentials = getCredentials(type);
      if (credentials != null) {
        return new Pair<>(credentials, type);
      }
    }
    var credentials = CredentialsType.UNKNOWN.getCredentials(myCredentialsTypeHolder);
    if (credentials != null) return new Pair<>(credentials, CredentialsType.UNKNOWN);
    throw new IllegalStateException("Unknown connection type");
  }

  /**
   * Returns credentials stored in this wrapper if the given type matches it, or {@code null} otherwise.<p/>
   * This method allows moving away from using {@link #switchType(CredentialsCase[])}.
   */
  public <T> @Nullable T getCredentials(@NotNull CredentialsType<T> credentialsType) {
    return credentialsType.getCredentials(myCredentialsTypeHolder);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public void switchType(CredentialsCase... cases) {
    var credentialsAndType = getCredentialsAndType();
    var type = credentialsAndType.second;
    for (var credentialsCase : cases) {
      if (credentialsCase.getType() == type) {
        credentialsCase.process(credentialsAndType.first);
        return;
      }
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof RemoteConnectionCredentialsWrapper w) {
      try {
        var credentials = getCredentials();
        var counterCredentials = w.getCredentials();
        return credentials.equals(counterCredentials);
      }
      catch (IllegalStateException e) {
        return false;
      }
    }
    return false;
  }

  public String getPresentableDetails(String interpreterPath) {
    return getTypeHandler().getPresentableDetails(interpreterPath);
  }
}
