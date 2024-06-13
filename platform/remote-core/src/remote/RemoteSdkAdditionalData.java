// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.Key;
import com.intellij.remote.ext.CredentialsCase;
import org.jetbrains.annotations.NotNull;

public interface RemoteSdkAdditionalData<T extends RemoteSdkCredentials>
  extends SdkAdditionalData, RemoteSdkCredentialsProducer<T>, RemoteSdkProperties {

  @NotNull RemoteConnectionCredentialsWrapper connectionCredentials();

  <C> void setCredentials(Key<C> key, C credentials);

  CredentialsType<?> getRemoteConnectionType();

  void switchOnConnectionType(CredentialsCase<?>... cases);
}
