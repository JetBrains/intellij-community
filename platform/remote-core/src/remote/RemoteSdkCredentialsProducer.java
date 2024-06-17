// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public interface RemoteSdkCredentialsProducer<T extends RemoteSdkCredentials> {
  T getRemoteSdkCredentials(@Nullable Project project, boolean allowSynchronousInteraction) throws InterruptedException, ExecutionException;

  void produceRemoteSdkCredentials(@Nullable Project project, boolean allowSynchronousInteraction, Consumer<? super T> remoteSdkCredentialsConsumer);

  Object getRemoteSdkDataKey();
}
