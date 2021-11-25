// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.remote.ext.CredentialsCase;
import com.intellij.remote.ext.RemoteCredentialsHandler;
import com.intellij.remote.ext.UnknownCredentialsHolder;
import com.intellij.remote.ext.UnknownTypeRemoteCredentialHandler;
import org.jetbrains.annotations.Nls;

public abstract class CredentialsType<T> {
  public static final ExtensionPointName<CredentialsType<?>> EP_NAME = new ExtensionPointName<>("com.intellij.remote.credentialsType");

  public static final Key<UnknownCredentialsHolder> UNKNOWN_CREDENTIALS = Key.create("UNKNOWN_CREDENTIALS");

  public static final CredentialsType<UnknownCredentialsHolder> UNKNOWN = new CredentialsType<>(
    RemoteBundle.message("credentials.type.filetype.description.unknown"), "") {
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

  private final @Nls(capitalization = Nls.Capitalization.Title) String myName;
  private final String myPrefix;
  private final String mySystemIndependentPrefix;
  private final String mySystemDependentPrefix;

  protected CredentialsType(@Nls(capitalization = Nls.Capitalization.Title) String name, String prefix) {
    myName = name;
    myPrefix = prefix;
    mySystemDependentPrefix = FileUtil.toSystemDependentName(prefix);
    mySystemIndependentPrefix = FileUtil.toSystemIndependentName(prefix);
  }

  public @Nls(capitalization = Nls.Capitalization.Title) String getName() {
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
    return path.startsWith(myPrefix) || path.startsWith(mySystemDependentPrefix) || path.startsWith(mySystemIndependentPrefix);
  }

  public abstract T createCredentials();

  public int getWeight() {
    return Integer.MAX_VALUE;
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
