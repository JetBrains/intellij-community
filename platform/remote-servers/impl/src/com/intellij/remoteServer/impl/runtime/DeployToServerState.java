// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.impl.runtime;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerRunConfigurationExtensionsManager;
import com.intellij.remoteServer.impl.runtime.deployment.DeploymentTaskImpl;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.remoteServer.runtime.deployment.debug.DebugConnector;
import com.intellij.remoteServer.runtime.ui.RemoteServersView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeployToServerState<S extends ServerConfiguration, D extends DeploymentConfiguration> implements RunProfileState {
  private final @NotNull RemoteServer<S> myServer;
  private final @NotNull DeploymentSource mySource;
  private final @NotNull D myConfiguration;
  private final @NotNull ExecutionEnvironment myEnvironment;

  public DeployToServerState(@NotNull RemoteServer<S> server, @NotNull DeploymentSource deploymentSource,
                             @NotNull D deploymentConfiguration, @NotNull ExecutionEnvironment environment) {
    myServer = server;
    mySource = deploymentSource;
    myConfiguration = deploymentConfiguration;
    myEnvironment = environment;
  }

  @Override
  public @Nullable ExecutionResult execute(Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
    final ServerConnection connection = ServerConnectionManager.getInstance().getOrCreateConnection(myServer);
    final Project project = myEnvironment.getProject();
    RemoteServersView.getInstance(project).showServerConnection(connection);

    final DebugConnector<?, ?> debugConnector;
    if (DefaultDebugExecutor.getDebugExecutorInstance().equals(executor)) {
      debugConnector = myServer.getType().createDebugConnector();
    }
    else {
      debugConnector = null;
    }

    DeploymentTask<D> task = new DeploymentTaskImpl<>(mySource, myConfiguration, project, debugConnector, myEnvironment);
    DeployToServerRunConfigurationExtensionsManager.getInstance().patchDeploymentTask(task);
    connection.deploy(task,
                      s -> RemoteServersView.getInstance(project).showDeployment(connection, (String)s));
    return null;
  }
}
