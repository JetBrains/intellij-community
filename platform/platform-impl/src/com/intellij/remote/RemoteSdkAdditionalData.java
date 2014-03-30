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

import com.intellij.openapi.projectRoots.SdkAdditionalData;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public interface RemoteSdkAdditionalData<T extends RemoteSdkCredentials>
  extends SdkAdditionalData, RemoteSdkProducer<T>, RemoteSdkProperties {
  void completeInitialization();

  boolean isInitialized();

  void setInitialized(boolean initialized);

  String getFullInterpreterPath();

  void setVagrantConnectionType(@NotNull VagrantBasedCredentialsHolder vagrantBasedCredentials);

  /**
   * This method switches to use of ssh-credentials based data
   * @param credentials credentials that specify connection
   */
  void setSshCredentials(@NotNull RemoteCredentialsHolder credentials);

  void setDeploymentConnectionType(@NotNull WebDeploymentCredentialsHolder credentials);

  CredentialsType getRemoteConnectionType();

  void switchOnConnectionType(RemoteSdkConnectionAcceptor acceptor);
}
