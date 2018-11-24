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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.actions.JavaRerunFailedTestsAction;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.execution.testframework.sm.runner.ui.SMRootTestProxyFormatter;
import com.intellij.execution.testframework.sm.runner.ui.TestTreeRenderer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.externalSystem.execution.ExternalSystemExecutionConsoleManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemExecuteTaskTask;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.action.GradleRerunFailedTestsAction;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;

/**
 * @author Vladislav.Soroka
 * @since 2/18/14
 */
public class GradleTestsExecutionConsoleManager
  implements ExternalSystemExecutionConsoleManager<ExternalSystemRunConfiguration, GradleTestsExecutionConsole, ProcessHandler> {

  @NotNull
  @Override
  public ProjectSystemId getExternalSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  @NotNull
  @Override
  public GradleTestsExecutionConsole attachExecutionConsole(@NotNull final ExternalSystemTask task,
                                                            @NotNull final Project project,
                                                            @NotNull final ExternalSystemRunConfiguration configuration,
                                                            @NotNull final Executor executor,
                                                            @NotNull final ExecutionEnvironment env,
                                                            @NotNull final ProcessHandler processHandler) throws ExecutionException {
    final GradleConsoleProperties consoleProperties = new GradleConsoleProperties(configuration, executor);
    String testFrameworkName = configuration.getSettings().getExternalSystemId().getReadableName();
    String splitterPropertyName = SMTestRunnerConnectionUtil.getSplitterPropertyName(testFrameworkName);
    final GradleTestsExecutionConsole consoleView = new GradleTestsExecutionConsole(consoleProperties, splitterPropertyName);
    consoleView.initTaskExecutionView(project, processHandler, task.getId());
    SMTestRunnerConnectionUtil.initConsoleView(consoleView, testFrameworkName);
    consoleView.attachToProcess(processHandler);

    final TestTreeView testTreeView = consoleView.getResultsViewer().getTreeView();
    if (testTreeView != null) {
      TestTreeRenderer originalRenderer = ObjectUtils.tryCast(testTreeView.getCellRenderer(), TestTreeRenderer.class);
      if (originalRenderer != null) {
        originalRenderer.setAdditionalRootFormatter(new SMRootTestProxyFormatter() {
          @Override
          public void format(@NotNull SMTestProxy.SMRootTestProxy testProxy, @NotNull TestTreeRenderer renderer) {
            final TestStateInfo.Magnitude magnitude = testProxy.getMagnitudeInfo();
            if (magnitude == TestStateInfo.Magnitude.RUNNING_INDEX) {
              renderer.clear();
              renderer.append(GradleBundle.message(
                "gradle.test.runner.ui.tests.tree.presentation.labels.waiting.tests"),
                              SimpleTextAttributes.REGULAR_ATTRIBUTES
              );
            }
            else if (!testProxy.isInProgress() && testProxy.isEmptySuite()) {
              renderer.clear();
              renderer.append(GradleBundle.message(
                "gradle.test.runner.ui.tests.tree.presentation.labels.no.tests.were.found"),
                              SimpleTextAttributes.REGULAR_ATTRIBUTES
              );
            }
          }
        });
      }
    }

    if (task instanceof ExternalSystemExecuteTaskTask) {
      final ExternalSystemExecuteTaskTask executeTask = (ExternalSystemExecuteTaskTask)task;
      if (executeTask.getArguments() == null || !StringUtil.contains(executeTask.getArguments(), "--tests")) {
        executeTask.appendArguments("--tests *");
      }
    }

    return consoleView;
  }

  @Override
  public void onOutput(@NotNull GradleTestsExecutionConsole executionConsole,
                       @NotNull ProcessHandler processHandler,
                       @NotNull String text,
                       @NotNull Key processOutputType) {
    GradleTestsExecutionConsoleOutputProcessor.onOutput(executionConsole, text, processOutputType);
  }

  @Override
  public boolean isApplicableFor(@NotNull ExternalSystemTask task) {
    if (task instanceof ExternalSystemExecuteTaskTask) {
      final ExternalSystemExecuteTaskTask taskTask = (ExternalSystemExecuteTaskTask)task;
      if (!StringUtil.equals(taskTask.getExternalSystemId().getId(), GradleConstants.SYSTEM_ID.getId())) return false;

      return ContainerUtil.find(taskTask.getTasksToExecute(), taskToExecute -> {
        String projectPath = taskTask.getExternalProjectPath();
        File file = new File(projectPath);
        if (file.isFile()) {
          projectPath = StringUtil.trimEnd(projectPath, "/" + file.getName());
        }
        final ExternalProjectInfo externalProjectInfo =
          ExternalSystemUtil.getExternalProjectInfo(taskTask.getIdeProject(), getExternalSystemId(), projectPath);
        if (externalProjectInfo == null) return false;

        final DataNode<TaskData> taskDataNode = GradleProjectResolverUtil.findTask(
          externalProjectInfo.getExternalProjectStructure(), projectPath, taskToExecute);
        return taskDataNode != null &&
               (("check".equals(taskDataNode.getData().getName()) && "verification".equals(taskDataNode.getData().getGroup())
                 || GradleCommonClassNames.GRADLE_API_TASKS_TESTING_TEST.equals(taskDataNode.getData().getType())));
      }) != null;
    }
    return false;
  }

  @Override
  public AnAction[] getRestartActions(@NotNull final GradleTestsExecutionConsole consoleView) {
    JavaRerunFailedTestsAction rerunFailedTestsAction =
      new GradleRerunFailedTestsAction(consoleView);
    rerunFailedTestsAction.setModelProvider(() -> consoleView.getResultsViewer());
    return new AnAction[]{rerunFailedTestsAction};
  }
}
