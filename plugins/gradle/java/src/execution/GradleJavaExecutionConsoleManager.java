// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GradleJavaExecutionConsoleManager extends GradleExecutionConsoleManager {

  @Override
  protected @NotNull ConsoleView createConsoleView(@NotNull Project project,
                                                   @NotNull ExternalSystemTask task,
                                                   @Nullable ExecutionEnvironment env) {
    var console = super.createConsoleView(project, task, env);

    if (env != null && env.getRunProfile() instanceof RunConfigurationBase<?> configuration) {
      return JavaRunConfigurationExtensionManager.getInstance().decorateExecutionConsole(
        configuration,
        env.getRunnerSettings(),
        console,
        env.getExecutor()
      );
    }

    return console;
  }
}
