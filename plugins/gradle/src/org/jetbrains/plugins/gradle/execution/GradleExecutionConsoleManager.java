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
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.service.execution.DefaultExternalSystemExecutionConsoleManager;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import static org.jetbrains.plugins.gradle.execution.GradleRunnerUtil.attachTaskExecutionView;

/**
 * @author Vladislav.Soroka
 * @since 11/27/2015
 */
public class GradleExecutionConsoleManager extends DefaultExternalSystemExecutionConsoleManager {

  @NotNull
  @Override
  public ProjectSystemId getExternalSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  @NotNull
  @Override
  public ExecutionConsole attachExecutionConsole(@NotNull ExternalSystemTask task,
                                                 @NotNull Project project,
                                                 @NotNull ExternalSystemRunConfiguration configuration,
                                                 @NotNull Executor executor,
                                                 @NotNull ExecutionEnvironment env,
                                                 @NotNull ProcessHandler processHandler) throws ExecutionException {
    final ConsoleView textConsole = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
    textConsole.attachToProcess(processHandler);
    return attachTaskExecutionView(project, textConsole, true, "gradle.runner.text.console", processHandler, task.getId());
  }
}
