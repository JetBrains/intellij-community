// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.build.BuildContentManager;
import com.intellij.build.BuildProgressListener;
import com.intellij.build.BuildViewManager;
import com.intellij.build.BuildViewSettingsProvider;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FailureResult;
import com.intellij.build.events.FileMessageEvent;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.ProgressBuildEvent;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.build.events.StartEvent;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testDiscovery.JvmToggleAutoTestAction;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction;
import com.intellij.execution.testframework.sm.runner.ui.TestTreeRenderer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.externalSystem.execution.ExternalSystemExecutionConsoleManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemProgressEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent;
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfigurationViewManager;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemExecuteTaskTask;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.action.GradleRerunFailedTestsAction;
import org.jetbrains.plugins.gradle.execution.build.GradleBuildViewSettingsProvider;
import org.jetbrains.plugins.gradle.execution.filters.ReRunTaskFilter;
import org.jetbrains.plugins.gradle.execution.test.runner.events.GradleTestEventsProcessor;
import org.jetbrains.plugins.gradle.execution.test.runner.events.GradleTestsExecutionConsoleOutputProcessor;
import org.jetbrains.plugins.gradle.service.execution.GradleCommandLineUtil;
import org.jetbrains.plugins.gradle.service.project.GradleTasksIndices;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import static org.jetbrains.plugins.gradle.settings.GradleExecutionSettings.RUN_AS_TEST_KEY;

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
    assert processHandler != null;
    if (env == null) return null;
    RunConfiguration configuration;
    SMTRunnerConsoleProperties consoleProperties = null;
    var settings = env.getRunnerAndConfigurationSettings();
    if (settings == null) {
      var runProfile = env.getRunProfile();
      if (runProfile instanceof AbstractImportTestsAction.ImportRunProfile importRunProfile) {
        configuration = importRunProfile.getInitialConfiguration();
        if (configuration instanceof SMRunnerConsolePropertiesProvider consolePropertiesProvider) {
          consoleProperties = consolePropertiesProvider.createTestConsoleProperties(DefaultRunExecutor.getRunExecutorInstance());
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
    var testFrameworkName = externalSystemRunConfiguration.getSettings().getExternalSystemId().getReadableName();
    var splitterPropertyName = SMTestRunnerConnectionUtil.getSplitterPropertyName(testFrameworkName);
    var consoleSettings = getExecutionConsoleSettings();
    var consoleView = new GradleTestsExecutionConsole(project, task.getId(), consoleProperties, splitterPropertyName);
    consoleView.attachToProcess(processHandler);
    SMTestRunnerConnectionUtil.initConsoleView(consoleView, testFrameworkName);

    var resultsViewer = consoleView.getResultsViewer();
    var testTreeView = resultsViewer.getTreeView();
    if (testTreeView != null) {
      if (testTreeView.getCellRenderer() instanceof TestTreeRenderer originalRenderer) {
        originalRenderer.setAdditionalRootFormatter((testProxy, renderer) -> {
          if (!testProxy.isInProgress() && testProxy.isEmptySuite()) {
            renderer.clear();
            renderer.append(GradleBundle.message("gradle.test.runner.ui.tests.tree.presentation.labels.no.tests.were.found"),
                            SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
        });
      }
    }
    var testsRootNode = resultsViewer.getTestsRootNode();
    testsRootNode.setExecutionId(env.getExecutionId());
    testsRootNode.setSuiteStarted();
    consoleView.getEventPublisher().onTestingStarted(testsRootNode);
    processHandler.addProcessListener(new ProcessListener() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        if (testsRootNode.isInProgress()) {
          if (event.getExitCode() == 1) {
            testsRootNode.setTestFailed("", null, false);
          }
          else {
            testsRootNode.setFinished();
          }
        }
      }

      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        // the single flow build view receives the process handler output via the external system event dispatcher
        if (!consoleSettings.isSingleBuildConsoleView()) {
          // the build view attaches the test console to the process handler, but the build tool window isn't attached
          consoleView.printToBuildView(event.getText(), outputType);
        }
      }
    });

    if (task instanceof ExternalSystemExecuteTaskTask taskTask) {
      consoleView.addMessageFilter(new ReRunTaskFilter(taskTask, env));
    }

    var buildViewManager = project.getService(BuildViewManager.class);
    var disposable = Disposer.newDisposable(buildViewManager, "Gradle test runner build event listener disposable");
    project.getService(ExternalSystemRunConfigurationViewManager.class).addListener(new BuildProgressListener() {
      @Override
      public void onEvent(@NotNull Object buildId, @NotNull BuildEvent event) {
        if (buildId != task.getId()) return;

        if (event instanceof FinishBuildEvent) {
          Disposer.dispose(disposable);
        }
        else if (event instanceof StartBuildEvent startBuildEvent) {
          // override start build event to use different execution console, toolbar actions etc.
          var buildDescriptor = startBuildEvent.getBuildDescriptor();
          var defaultBuildDescriptor = new DefaultBuildDescriptor(
            buildDescriptor.getId(), buildDescriptor.getTitle(),
            buildDescriptor.getWorkingDir(), buildDescriptor.getStartTime()
          );

          // do not open Build tw for any error messages as it can be tests failure events
          defaultBuildDescriptor.setActivateToolWindowWhenFailed(false);
          event = StartBuildEvent.builder(event.getMessage(), defaultBuildDescriptor)
            .withBuildViewSettings(GradleBuildViewSettingsProvider.INSTANCE)
            .build();
        }
        buildViewManager.onEvent(buildId, event);

        if (event instanceof StartEvent) {
          buildViewManager.onEvent(buildId, ProgressBuildEvent.builder(event.getId(), event.getMessage())
            .withParentId(event.getParentId())
            .withTime(event.getEventTime())
            .withHint("- " + GradleBundle.message("gradle.test.runner.build.tw.link.title"))
            .build());
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
    if (event instanceof FileMessageEvent messageEvent && messageEvent.getKind() == MessageEvent.Kind.ERROR) {
      openBuildToolWindow(project);
      return;
    }

    // open Build tw for recognized build failures like startup errors
    if (event instanceof FinishBuildEvent finishEvent && finishEvent.getResult() instanceof FailureResult failureResult) {
      if (!failureResult.getFailures().isEmpty()) {
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

  private static void openBuildToolWindow(@NotNull Project project) {
    ApplicationManager.getApplication().invokeLater(() -> {
      var toolWindow = BuildContentManager.getInstance(project).getOrCreateToolWindow();
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
    var consoleSettings = ExternalSystemUtil.getExecutionConsoleSettings(this, executionConsole);
    GradleTestsExecutionConsoleOutputProcessor.onOutput(executionConsole, consoleSettings, processHandler, text, processOutputType);
  }

  @Override
  public void onStatusChange(@NotNull GradleTestsExecutionConsole executionConsole, @NotNull ExternalSystemTaskNotificationEvent event) {
    if (event instanceof ExternalSystemTaskExecutionEvent executionEvent) {
      var progressEvent = executionEvent.getProgressEvent();
      if (progressEvent.getDescriptor() instanceof TestOperationDescriptor) {
        //noinspection unchecked
        var testProgressEvent = (ExternalSystemProgressEvent<TestOperationDescriptor>)progressEvent;
        GradleTestEventsProcessor.onStatusChange(executionConsole, testProgressEvent);
      }
    }
  }

  @Override
  public boolean isApplicableFor(@NotNull ExternalSystemTask task) {
    if (task instanceof ExternalSystemExecuteTaskTask taskTask) {
      if (GradleConstants.SYSTEM_ID.equals(taskTask.getExternalSystemId())) {
        if (Boolean.TRUE.equals(taskTask.getUserData(RUN_AS_TEST_KEY))) {
          return true;
        }
        if (hasTestTasks(taskTask)) {
          taskTask.putUserData(RUN_AS_TEST_KEY, true);
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
  public AnAction[] getRestartActions(@NotNull GradleTestsExecutionConsole consoleView) {
    var rerunFailedTestsAction = new GradleRerunFailedTestsAction(consoleView);
    rerunFailedTestsAction.setModelProvider(consoleView::getResultsViewer);
    return new AnAction[]{rerunFailedTestsAction, new JvmToggleAutoTestAction()};
  }

  @Override
  public @NotNull BuildViewSettingsProvider getExecutionConsoleSettings() {
    return new BuildViewSettingsProvider() {
      @Override
      public boolean isExecutionViewHidden() {
        // the test console has its own tests tree, so the run tab shows it instead of the build tree
        return true;
      }

      @Override
      public boolean isSingleBuildConsoleView() {
        return GradleBuildViewSettingsProvider.INSTANCE.isSingleBuildConsoleView();
      }
    };
  }
}
