// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

final class AntRunner implements ProgramRunner<RunnerSettings> {
  public static final @NonNls String EXECUTOR_ID = "AntRunConfigurationRunner";

  @Override
  public void execute(@NotNull ExecutionEnvironment environment) throws ExecutionException {
    ExecutionManager.getInstance(environment.getProject()).startRunProfile(environment, state -> {
      FileDocumentManager.getInstance().saveAllDocuments();
      ExecutionResult executionResult = state.execute(environment.getExecutor(), this);
      if (executionResult == null) {
        return null;
      }
      return new RunContentDescriptor(executionResult.getExecutionConsole(), executionResult.getProcessHandler(), executionResult.getExecutionConsole().getComponent(), environment.getRunProfile().getName());
    });
  }

  @NotNull
  @Override
  public @NonNls String getRunnerId() {
    return EXECUTOR_ID;
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return DefaultRunExecutor.EXECUTOR_ID.equals(executorId) && profile instanceof AntRunConfiguration;
  }
}
