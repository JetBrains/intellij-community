// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.execution;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.lang.ant.config.AntBuildListener;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

final class AntRunProfileState implements RunProfileState {
  static final Key<AntBuildMessageView> MESSAGE_VIEW = Key.create("ANT_MESSAGE_VIEW");
  private final ExecutionEnvironment myEnvironment;

  AntRunProfileState(ExecutionEnvironment environment) {
    myEnvironment = environment;
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner<?> runner) {
    final RunProfile profile = myEnvironment.getRunProfile();
    if (profile instanceof AntRunConfiguration) {
      final AntRunConfiguration runConfig = (AntRunConfiguration)profile;
      if (runConfig.getTarget() == null) {
        return null;
      }
      final ProcessHandler processHandler = ExecutionHandler.executeRunConfiguration(runConfig, myEnvironment.getDataContext(), runConfig.getProperties(), AntBuildListener.NULL);
      if (processHandler == null) {
        return null;
      }

      return new DefaultExecutionResult(new ExecutionConsole() {
        @NotNull
        @Override
        public JComponent getComponent() {
          return processHandler.getUserData(MESSAGE_VIEW);
        }

        @Override
        public JComponent getPreferredFocusableComponent() {
          return processHandler.getUserData(MESSAGE_VIEW);
        }

        @Override
        public void dispose() {
          processHandler.putUserData(MESSAGE_VIEW, null);
        }
      }, processHandler);
    }
    return null;
  }
}
