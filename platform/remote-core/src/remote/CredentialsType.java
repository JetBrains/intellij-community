// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.remote.ext.CredentialsCase;
import com.intellij.remote.ext.RemoteCredentialsHandler;
import com.intellij.remote.ext.UnknownCredentialsHolder;
import com.intellij.remote.ext.UnknownTypeRemoteCredentialHandler;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class CredentialsType<T> {
  public static final ExtensionPointName<CredentialsType<?>> EP_NAME = new ExtensionPointName<>("com.intellij.remote.credentialsType");

  public static final Key<UnknownCredentialsHolder> UNKNOWN_CREDENTIALS = Key.create("UNKNOWN_CREDENTIALS");

  public static final CredentialsType<UnknownCredentialsHolder> UNKNOWN = new CredentialsType<>(RemoteBundle.message("credentials.type.filetype.description.unknown"), "") {
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
  private final @NotNull Set<String> myPrefixes;

  protected CredentialsType(@Nls(capitalization = Nls.Capitalization.Title) String name, String prefix) {
    this(name, List.of(prefix));
  }

  protected CredentialsType(@Nls(capitalization = Nls.Capitalization.Title) String name, Collection<String> prefixes) {
    myName = name;
    myPrefixes = prefixes.stream()
      .flatMap(prefix -> Stream.of(prefix, FileUtil.toSystemDependentName(prefix), FileUtil.toSystemIndependentName(prefix)))
      .collect(Collectors.toUnmodifiableSet());
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
    return ContainerUtil.exists(myPrefixes, prefix -> path.startsWith(prefix));
  }

  public abstract T createCredentials();

  public int getWeight() {
    return Integer.MAX_VALUE;
  }

  public void saveCredentials(RemoteSdkAdditionalData data, CredentialsCase<T>... cases) {
    for (var credentialsCase : cases) {
      if (credentialsCase.getType() == this) {
        T credentials = createCredentials();
        credentialsCase.process(credentials);
        data.setCredentials(getCredentialsKey(), credentials);
      }
    }
  }
}
