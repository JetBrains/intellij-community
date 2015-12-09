/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

/**
 * @author Irina.Chernushina on 12/9/2015.
 */
public class DefaultRemoteSdkConnectionAcceptor implements RemoteSdkConnectionAcceptor {
  private CredentialsType myCredentialsType;

  @Override
  public void ssh(@NotNull RemoteCredentialsHolder cred) {
    myCredentialsType = CredentialsType.SSH_HOST;
  }

  @Override
  public void vagrant(@NotNull VagrantBasedCredentialsHolder cred) {
    myCredentialsType = CredentialsType.VAGRANT;
  }

  @Override
  public void deployment(@NotNull WebDeploymentCredentialsHolder cred) {
    myCredentialsType = CredentialsType.WEB_DEPLOYMENT;
  }

  @Override
  public void docker(@NotNull DockerCredentialsHolder cred) {
    myCredentialsType = CredentialsType.DOCKER;
  }

  public CredentialsType getCredentialsType() {
    return myCredentialsType;
  }

  public static CredentialsType getRemoteConnectionType(@NotNull final RemoteConnectionCredentialsWrapper wrapper) {
    final DefaultRemoteSdkConnectionAcceptor acceptor = new DefaultRemoteSdkConnectionAcceptor();
    wrapper.switchType(acceptor);
    return acceptor.getCredentialsType();
  }
}
