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
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.console.DuplexConsoleView;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static org.jetbrains.plugins.gradle.execution.GradleRunnerUtil.attachTaskExecutionView;

/**
 * @author Vladislav.Soroka
 * @since 10/28/2015
 */
public class GradleTestsExecutionConsole extends SMTRunnerConsoleView {
  private Map<String, SMTestProxy> testsMap = ContainerUtil.newHashMap();
  private StringBuilder myBuffer = new StringBuilder();
  private DuplexConsoleView myConsoleView;

  public GradleTestsExecutionConsole(TestConsoleProperties consoleProperties, @Nullable String splitterProperty) {
    super(consoleProperties, splitterProperty);
  }

  public void initTaskExecutionView(Project project, ProcessHandler processHandler, ExternalSystemTaskId taskId) {
    myConsoleView = attachTaskExecutionView(project, super.getConsole(), false, "gradle.test.runner.text.console", processHandler, taskId);
  }

  @NotNull
  @Override
  public ConsoleView getConsole() {
    return myConsoleView != null ? myConsoleView : super.getConsole();
  }

  public Map<String, SMTestProxy> getTestsMap() {
    return testsMap;
  }

  public StringBuilder getBuffer() {
    return myBuffer;
  }

  @Override
  public void dispose() {
    super.dispose();
    Disposer.dispose(myConsoleView);
  }

  public SMTestLocator getUrlProvider() {
    return GradleUrlProvider.INSTANCE;
  }
}
