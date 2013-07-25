/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.remoteServer.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.deployment.Deployer;
import com.intellij.remoteServer.deployment.DeploymentSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class DeployToServerState<C extends ServerConfiguration> implements RunProfileState {
  @NotNull private final Deployer<C> myDeployer;
  @NotNull private final RemoteServer<C> myServer;
  @NotNull private final DeploymentSource mySource;

  public DeployToServerState(@NotNull Deployer<C> deployer,
                             @NotNull RemoteServer<C> server,
                             @NotNull DeploymentSource deploymentSource) {
    myDeployer = deployer;
    myServer = server;
    mySource = deploymentSource;
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    myDeployer.startDeployment(myServer, mySource);
    return null;
  }
}
