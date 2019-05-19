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

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public interface RemoteSdkCredentialsProducer<T extends RemoteSdkCredentials> {
  /**
   * Synchronously returns remote sdk credentials
   * @return
   * @throws InterruptedException
   * @deprecated
   */
  @Deprecated
  T getRemoteSdkCredentials() throws InterruptedException, ExecutionException;

  /**
   * Returns remote sdk credentials for instances saved on application level,
   * e.g. only application level deployment configurations will be available.
   * @return
   * @throws InterruptedException
   * @throws ExecutionException
   * @deprecated
   */
  @Deprecated
  T getRemoteSdkCredentials(boolean allowSynchronousInteraction) throws InterruptedException, ExecutionException;

  T getRemoteSdkCredentials(@Nullable Project project, boolean allowSynchronousInteraction) throws InterruptedException, ExecutionException;

  /**
   * Produces remote sdk credentials for instances saved on application level,
   * e.g. only application level deployment configurations will be available.
   * @param allowSynchronousInteraction
   * @param remoteSdkCredentialsConsumer
   * @deprecated
   */
  @Deprecated
  void produceRemoteSdkCredentials(boolean allowSynchronousInteraction, Consumer<T> remoteSdkCredentialsConsumer);

  void produceRemoteSdkCredentials(@Nullable Project project, boolean allowSynchronousInteraction, Consumer<T> remoteSdkCredentialsConsumer);

  /**
   * @param remoteSdkCredentialsConsumer
   * @deprecated
   */
  @Deprecated
  void produceRemoteSdkCredentials(Consumer<T> remoteSdkCredentialsConsumer);

  Object getRemoteSdkDataKey();
}
