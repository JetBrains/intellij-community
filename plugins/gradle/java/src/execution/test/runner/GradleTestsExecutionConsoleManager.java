// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testDiscovery.JvmToggleAutoTestAction;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.action.GradleRerunFailedTestsAction;
import org.jetbrains.plugins.gradle.execution.filters.ReRunTaskFilter;
import org.jetbrains.plugins.gradle.execution.test.runner.events.GradleTestEventsProcessor;
import org.jetbrains.plugins.gradle.execution.test.runner.events.GradleTestsExecutionConsoleOutputProcessor;
import org.jetbrains.plugins.gradle.service.execution.GradleCommandLineUtil;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;
import org.jetbrains.plugins.gradle.service.project.GradleTasksIndices;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * @author Vladislav.Soroka
 */
public final class GradleTestsExecutionConsoleManager
  implements ExternalSystemExecutionConsoleManager<GradleTestsExecutionConsole, ProcessHandler> {

  @Override
  public @NotNull ProjectSystemId getExternalSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  @Override
  public @Nullable GradleTestsExecutionConsole attachExecutionConsole(@NotNull Project project,
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
    if (!(configuration instanceof ExternalSystemRunConfiguration externalSystemRunConfiguration)) return null;

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
      processHandler.addProcessListener(new ProcessListener() {
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
    }, ModalityState.nonModal(), project.getDisposed());
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
    if (task instanceof ExternalSystemExecuteTaskTask taskTask) {
      if (StringUtil.equals(taskTask.getExternalSystemId().getId(), GradleConstants.SYSTEM_ID.getId())) {
        var isRunAsTest = taskTask.getUserData(GradleRunConfiguration.RUN_AS_TEST_KEY);
        if (ObjectUtils.chooseNotNull(isRunAsTest, false)) {
          return true;
        }
        if (hasTestTasks(taskTask)) {
          taskTask.putUserData(GradleRunConfiguration.RUN_AS_TEST_KEY, true);
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Checks that ES task has a tasks which can produce test events.
   * <p>
   * Note: This function has specific heuristics for recognition which task can produce test events.
   * Therefore, this function cannot be reused in the other place.
   */
  private static boolean hasTestTasks(@NotNull ExternalSystemExecuteTaskTask taskTask) {
    var project = taskTask.getIdeProject();
    var modulePath = taskTask.getExternalProjectPath();
    var commandLine = GradleCommandLineUtil.parseCommandLine(taskTask.getTasksToExecute(), taskTask.getArguments());
    var indices = GradleTasksIndices.getInstance(project);
    for (var task : commandLine.getTasks()) {
      if (!GradleCommandLineUtil.getTestPatterns(task).isEmpty()) {
        return true;
      }
      var taskData = indices.findTasks(modulePath, task.getName());
      for (var taskDatum : taskData) {
        if (taskDatum.isTest()) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public AnAction[] getRestartActions(final @NotNull GradleTestsExecutionConsole consoleView) {
    JavaRerunFailedTestsAction rerunFailedTestsAction =
      new GradleRerunFailedTestsAction(consoleView);
    rerunFailedTestsAction.setModelProvider(() -> consoleView.getResultsViewer());
    return new AnAction[]{rerunFailedTestsAction, new JvmToggleAutoTestAction()};
  }
}
