// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.runtime.deployment;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.remoteServer.runtime.deployment.debug.DebugConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class DeploymentTaskImpl<D extends DeploymentConfiguration> implements DeploymentTask<D> {
  private final DeploymentSource mySource;
  private final D myConfiguration;
  private final Project myProject;
  private final DebugConnector<?,?> myDebugConnector;
  private final ExecutionEnvironment myExecutionEnvironment;

  public DeploymentTaskImpl(DeploymentSource source, D configuration, Project project, DebugConnector<?, ?> connector,
                            ExecutionEnvironment environment) {
    mySource = source;
    myConfiguration = configuration;
    myProject = project;
    myDebugConnector = connector;
    myExecutionEnvironment = environment;
  }

  @Override
  @NotNull
  public DeploymentSource getSource() {
    return mySource;
  }

  @Override
  @NotNull
  public D getConfiguration() {
    return myConfiguration;
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  public boolean isDebugMode() {
    return myDebugConnector != null;
  }

  @Nullable
  public DebugConnector<?, ?> getDebugConnector() {
    return myDebugConnector;
  }

  @Override
  @NotNull
  public ExecutionEnvironment getExecutionEnvironment() {
    return myExecutionEnvironment;
  }
}
