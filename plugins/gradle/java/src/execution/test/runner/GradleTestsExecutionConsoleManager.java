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

import com.intellij.build.*;
import com.intellij.build.events.*;
import com.intellij.build.events.impl.ProgressBuildEventImpl;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.JavaRerunFailedTestsAction;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction;
import com.intellij.execution.testframework.sm.runner.ui.SMRootTestProxyFormatter;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.execution.testframework.sm.runner.ui.TestTreeRenderer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.externalSystem.execution.ExternalSystemExecutionConsoleManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemProgressEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent;
import com.intellij.openapi.externalSystem.model.task.event.OperationDescriptor;
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfigurationViewManager;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemExecuteTaskTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.action.GradleRerunFailedTestsAction;
import org.jetbrains.plugins.gradle.execution.filters.ReRunTaskFilter;
import org.jetbrains.plugins.gradle.service.project.GradleTasksIndices;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleTaskData;

import java.io.File;

import static org.jetbrains.plugins.gradle.util.GradleConstants.RUN_TASK_AS_TEST;

/**
 * @author Vladislav.Soroka
 */
public class GradleTestsExecutionConsoleManager
  implements ExternalSystemExecutionConsoleManager<GradleTestsExecutionConsole, ProcessHandler> {

  @NotNull
  @Override
  public ProjectSystemId getExternalSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  @Nullable
  @Override
  public GradleTestsExecutionConsole attachExecutionConsole(@NotNull Project project,
                                                            @NotNull ExternalSystemTask task,
                                                            @Nullable ExecutionEnvironment env,
                                                            @Nullable ProcessHandler processHandler) {
    if (env == null) return null;
    RunConfiguration configuration;
    SMTRunnerConsoleProperties consoleProperties = null;
    RunnerAndConfigurationSettings settings = env.getRunnerAndConfigurationSettings();
    if (settings == null) {
      RunProfile runProfile = env.getRunProfile();
      if (runProfile instanceof AbstractImportTestsAction.ImportRunProfile) {
        configuration = ((AbstractImportTestsAction.ImportRunProfile)runProfile).getInitialConfiguration();
        if (configuration instanceof SMRunnerConsolePropertiesProvider) {
          consoleProperties = ((SMRunnerConsolePropertiesProvider)configuration).createTestConsoleProperties(DefaultRunExecutor.getRunExecutorInstance());
        }
      }
      else {
        return null;
      }
    }
    else {
      configuration = settings.getConfiguration();
    }
    if (!(configuration instanceof ExternalSystemRunConfiguration)) return null;
    ExternalSystemRunConfiguration externalSystemRunConfiguration = (ExternalSystemRunConfiguration)configuration;

    if (consoleProperties == null) {
      consoleProperties = new GradleConsoleProperties(externalSystemRunConfiguration, env.getExecutor());
    }
    String testFrameworkName = externalSystemRunConfiguration.getSettings().getExternalSystemId().getReadableName();
    String splitterPropertyName = SMTestRunnerConnectionUtil.getSplitterPropertyName(testFrameworkName);
    GradleTestsExecutionConsole consoleView =
      new GradleTestsExecutionConsole(project, task.getId(), consoleProperties, splitterPropertyName);
    SMTestRunnerConnectionUtil.initConsoleView(consoleView, testFrameworkName);

    SMTestRunnerResultsForm resultsViewer = consoleView.getResultsViewer();
    final TestTreeView testTreeView = resultsViewer.getTreeView();
    if (testTreeView != null) {
      TestTreeRenderer originalRenderer = ObjectUtils.tryCast(testTreeView.getCellRenderer(), TestTreeRenderer.class);
      if (originalRenderer != null) {
        originalRenderer.setAdditionalRootFormatter(new SMRootTestProxyFormatter() {
          @Override
          public void format(@NotNull SMTestProxy.SMRootTestProxy testProxy, @NotNull TestTreeRenderer renderer) {
            if (!testProxy.isInProgress() && testProxy.isEmptySuite()) {
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
    SMTestProxy.SMRootTestProxy testsRootNode = resultsViewer.getTestsRootNode();
    testsRootNode.setExecutionId(env.getExecutionId());
    testsRootNode.setSuiteStarted();
    consoleView.getEventPublisher().onTestingStarted(testsRootNode);
    if (processHandler != null) {
      processHandler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          if (testsRootNode.isInProgress()) {
            ApplicationManager.getApplication().invokeLater(() -> {
              if (event.getExitCode() == 1) {
                testsRootNode.setTestFailed("", null, false);
              }
              else {
                testsRootNode.setFinished();
              }
              resultsViewer.onBeforeTestingFinished(testsRootNode);
              resultsViewer.onTestingFinished(testsRootNode);
            });
          }
        }
      });
    }

    if (task instanceof ExternalSystemExecuteTaskTask) {
      final ExternalSystemExecuteTaskTask executeTask = (ExternalSystemExecuteTaskTask)task;
      if (executeTask.getArguments() == null || !StringUtil.contains(executeTask.getArguments(), GradleConstants.TESTS_ARG_NAME)) {
        executeTask.appendArguments("--tests *");
      }
      consoleView.addMessageFilter(new ReRunTaskFilter((ExternalSystemExecuteTaskTask)task, env));
    }

    Disposable disposable = Disposer.newDisposable(consoleView, "Gradle test runner build event listener disposable");
    BuildViewManager buildViewManager = project.getService(BuildViewManager.class);
    project.getService(ExternalSystemRunConfigurationViewManager.class).addListener(new BuildProgressListener() {
      @Override
      public void onEvent(@NotNull Object buildId, @NotNull BuildEvent event) {
        if (buildId != task.getId()) return;

        if (event instanceof FinishBuildEvent) {
          Disposer.dispose(disposable);
        }
        else if (event instanceof StartBuildEvent) {
          // override start build event to use different execution console, toolbar actions etc.
          BuildDescriptor buildDescriptor = ((StartBuildEvent)event).getBuildDescriptor();
          DefaultBuildDescriptor defaultBuildDescriptor =
            new DefaultBuildDescriptor(buildDescriptor.getId(), buildDescriptor.getTitle(),
                                       buildDescriptor.getWorkingDir(), buildDescriptor.getStartTime());

          // do not open Build tw for any error messages as it can be tests failure events
          defaultBuildDescriptor.setActivateToolWindowWhenFailed(false);
          event = new StartBuildEventImpl(defaultBuildDescriptor, event.getMessage());
        }
        buildViewManager.onEvent(buildId, event);

        if (event instanceof StartEvent) {
          ProgressBuildEventImpl progressBuildEvent =
            new ProgressBuildEventImpl(event.getId(), event.getParentId(), event.getEventTime(), event.getMessage(), -1, -1, "");
          progressBuildEvent.setHint("- " + GradleBundle.message("gradle.test.runner.build.tw.link.title"));
          buildViewManager.onEvent(buildId, progressBuildEvent);
        }

        maybeOpenBuildToolWindow(event, project, testsRootNode);
      }
    }, disposable);
    return consoleView;
  }

  private static void maybeOpenBuildToolWindow(@NotNull BuildEvent event,
                                               @NotNull Project project,
                                               @NotNull SMTestProxy.SMRootTestProxy testsRootNode) {
    // open Build tw for file error, as it usually comes from compilation
    if ((event instanceof FileMessageEvent) && ((FileMessageEvent)event).getKind() == MessageEvent.Kind.ERROR) {
      openBuildToolWindow(project);
      return;
    }

    // open Build tw for recognized build failures like startup errors
    if (event instanceof FinishBuildEvent) {
      EventResult buildResult = ((FinishBuildEvent)event).getResult();
      if (buildResult instanceof FailureResult) {
        if (!((FailureResult)buildResult).getFailures().isEmpty()) {
          openBuildToolWindow(project);
        }
        else {
          ApplicationManager.getApplication().invokeLater(() -> {
            if (!testsRootNode.isInProgress() && testsRootNode.isEmptySuite()) {
              openBuildToolWindow(project);
            }
          });
        }
      }
    }
  }

  private static void openBuildToolWindow(@NotNull Project project) {
    ApplicationManager.getApplication().invokeLater(() -> {
      ToolWindow toolWindow = BuildContentManager.getInstance(project).getOrCreateToolWindow();
      if (toolWindow.isAvailable() && !toolWindow.isVisible()) {
        toolWindow.show(null);
      }
    }, ModalityState.NON_MODAL, project.getDisposed());
  }

  @Override
  public void onOutput(@NotNull GradleTestsExecutionConsole executionConsole,
                       @NotNull ProcessHandler processHandler,
                       @NotNull String text,
                       @NotNull Key processOutputType) {
    GradleTestsExecutionConsoleOutputProcessor.onOutput(executionConsole, text, processOutputType);
  }

  @Override
  public void onStatusChange(@NotNull GradleTestsExecutionConsole executionConsole, @NotNull ExternalSystemTaskNotificationEvent event) {
    if (event instanceof ExternalSystemTaskExecutionEvent) {
      ExternalSystemProgressEvent progressEvent = ((ExternalSystemTaskExecutionEvent)event).getProgressEvent();
      OperationDescriptor descriptor = progressEvent.getDescriptor();
      if (descriptor instanceof TestOperationDescriptor) {
        //noinspection unchecked
        GradleTestEventsProcessor.onStatusChange(executionConsole, progressEvent);
      }
    }
  }

  @Override
  public boolean isApplicableFor(@NotNull ExternalSystemTask task) {
    if (task instanceof ExternalSystemExecuteTaskTask) {
      var taskTask = (ExternalSystemExecuteTaskTask)task;
      if (StringUtil.equals(taskTask.getExternalSystemId().getId(), GradleConstants.SYSTEM_ID.getId())) {
        if (hasTestOption(taskTask) || hasTestTasks(taskTask)) {
          taskTask.putUserData(RUN_TASK_AS_TEST, true);
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasTestOption(@NotNull ExternalSystemExecuteTaskTask task) {
    var options = task.getArguments();
    var tasksAndArguments = task.getTasksToExecute();
    return options != null && StringUtil.contains(options, GradleConstants.TESTS_ARG_NAME)
           || tasksAndArguments.contains(GradleConstants.TESTS_ARG_NAME);
  }

  private static boolean hasTestTasks(@NotNull ExternalSystemExecuteTaskTask task) {
    var modulePath = getModulePath(task);
    var tasksAndArguments = task.getTasksToExecute();
    var tasksIndices = GradleTasksIndices.getInstance(task.getIdeProject());
    var tasks = tasksIndices.findTasks(modulePath, tasksAndArguments);
    return ContainerUtil.or(tasks, it -> isTestTask(it));
  }

  private static boolean isTestTask(@Nullable GradleTaskData task) {
    return task != null && (task.isTest() || "check".equals(task.getName()) && "verification".equals(task.getGroup()));
  }

  private static @NotNull String getModulePath(@NotNull ExternalSystemExecuteTaskTask task) {
    var externalProjectPath = task.getExternalProjectPath();
    var file = new File(externalProjectPath);
    if (file.isFile()) {
      return StringUtil.trimEnd(externalProjectPath, "/" + file.getName());
    }
    return externalProjectPath;
  }

  @Override
  public AnAction[] getRestartActions(@NotNull final GradleTestsExecutionConsole consoleView) {
    JavaRerunFailedTestsAction rerunFailedTestsAction =
      new GradleRerunFailedTestsAction(consoleView);
    rerunFailedTestsAction.setModelProvider(() -> consoleView.getResultsViewer());
    return new AnAction[]{rerunFailedTestsAction};
  }
}
