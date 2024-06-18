// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.Key;
import com.intellij.remote.ext.CredentialsCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public interface RemoteSdkAdditionalData<T extends RemoteSdkCredentials>
  extends SdkAdditionalData, RemoteSdkCredentialsProducer<T>, RemoteSdkProperties {

  @NotNull RemoteConnectionCredentialsWrapper connectionCredentials();

  <C> void setCredentials(Key<C> key, C credentials);

  CredentialsType<?> getRemoteConnectionType();

  void switchOnConnectionType(CredentialsCase<?>... cases);

  default RemoteCredentials getRemoteCredentials(@Nullable Project project, boolean allowSynchronousInteraction) throws InterruptedException, ExecutionException {
    return getRemoteSdkCredentials(project, allowSynchronousInteraction);
  }

  default void produceRemoteCredentials(@Nullable Project project, boolean allowSynchronousInteraction, Consumer<RemoteCredentials> consumer) {
    produceRemoteSdkCredentials(project, allowSynchronousInteraction, consumer);
  }
}
