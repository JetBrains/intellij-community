// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

public interface RemoteSdkCredentialsProducer<T extends RemoteSdkCredentials> {
  /**
   * Synchronously returns remote sdk credentials
   *
   * @deprecated use {@link #getRemoteSdkCredentials(Project, boolean)}
   */
  @Deprecated
  T getRemoteSdkCredentials() throws InterruptedException, ExecutionException;

  /**
   * Returns remote sdk credentials for instances saved on application level,
   * e.g. only application level deployment configurations will be available.
   *
   * @deprecated use {@link #getRemoteSdkCredentials(Project, boolean)}
   */
  @Deprecated
  T getRemoteSdkCredentials(boolean allowSynchronousInteraction) throws InterruptedException, ExecutionException;

  T getRemoteSdkCredentials(@Nullable Project project, boolean allowSynchronousInteraction) throws InterruptedException, ExecutionException;

  /**
   * Produces remote sdk credentials for instances saved on application level,
   * e.g. only application level deployment configurations will be available.
   *
   * @param allowSynchronousInteraction
   * @param remoteSdkCredentialsConsumer
   * @deprecated use {@link #produceRemoteSdkCredentials(Project, boolean, Consumer)}
   */
  @Deprecated
  void produceRemoteSdkCredentials(boolean allowSynchronousInteraction, Consumer<? super T> remoteSdkCredentialsConsumer);

  void produceRemoteSdkCredentials(@Nullable Project project,
                                   boolean allowSynchronousInteraction,
                                   Consumer<? super T> remoteSdkCredentialsConsumer);

  /**
   * @param remoteSdkCredentialsConsumer
   * @deprecated use {@link #produceRemoteSdkCredentials(Project, boolean, Consumer)}
   */
  @Deprecated
  void produceRemoteSdkCredentials(Consumer<? super T> remoteSdkCredentialsConsumer);

  Object getRemoteSdkDataKey();
}
