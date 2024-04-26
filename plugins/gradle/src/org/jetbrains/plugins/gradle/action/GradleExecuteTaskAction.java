// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.action;

import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.ide.actions.runAnything.RunAnythingManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.externalSystem.action.ExternalSystemAction;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.view.ExternalProjectsView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.gradle.cli.CommandLineArgumentException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLine;

import static org.jetbrains.plugins.gradle.execution.GradleRunAnythingProvider.HELP_COMMAND;

/**
 * @author Vladislav.Soroka
 */
public class GradleExecuteTaskAction extends ExternalSystemAction {

  @Override
  protected boolean isVisible(@NotNull AnActionEvent e) {
    if (!super.isVisible(e)) return false;
    final ExternalProjectsView projectsView = e.getData(ExternalSystemDataKeys.VIEW);
    return projectsView == null || GradleConstants.SYSTEM_ID.equals(getSystemId(e));
  }

  @Override
  protected boolean isEnabled(@NotNull AnActionEvent e) {
    return true;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation p = e.getPresentation();
    p.setVisible(isVisible(e));
    p.setEnabled(isEnabled(e));
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;
    RunAnythingManager runAnythingManager = RunAnythingManager.getInstance(project);
    runAnythingManager.show(HELP_COMMAND + " ", false, e);
  }

  public static void runGradle(@NotNull Project project,
                               @Nullable Executor executor,
                               @NotNull String workDirectory,
                               @NotNull @NlsSafe String fullCommandLine) {
    final ExternalTaskExecutionInfo taskExecutionInfo;
    try {
      taskExecutionInfo = buildTaskInfo(workDirectory, fullCommandLine, executor);
    }
    catch (CommandLineArgumentException ex) {
      @NlsSafe String italicFormat = "<i>%s</i> \n";
      final NotificationData notificationData = new NotificationData(
        GradleBundle.message("gradle.command.line.parse.error.invalid.arguments"),
        String.format(italicFormat, fullCommandLine) + ex.getMessage(),
        NotificationCategory.WARNING, NotificationSource.TASK_EXECUTION
      );
      notificationData.setBalloonNotification(true);
      ExternalSystemNotificationManager.getInstance(project).showNotification(GradleConstants.SYSTEM_ID, notificationData);
      return;
    }

    ExternalSystemUtil.runTask(taskExecutionInfo.getSettings(), taskExecutionInfo.getExecutorId(), project, GradleConstants.SYSTEM_ID, null,
                               ProgressExecutionMode.NO_PROGRESS_ASYNC);

    RunnerAndConfigurationSettings configuration =
      ExternalSystemUtil.createExternalSystemRunnerAndConfigurationSettings(taskExecutionInfo.getSettings(),
                                                                            project, GradleConstants.SYSTEM_ID);
    if (configuration == null) return;

    RunManager runManager = RunManager.getInstance(project);
    final RunnerAndConfigurationSettings existingConfiguration =
      runManager.findConfigurationByTypeAndName(configuration.getType(), configuration.getName());
    if (existingConfiguration == null) {
      runManager.setTemporaryConfiguration(configuration);
    }
    else {
      runManager.setSelectedConfiguration(existingConfiguration);
    }
  }

  private static ExternalTaskExecutionInfo buildTaskInfo(
    @NotNull String projectPath,
    @NotNull String fullCommandLine,
    @Nullable Executor executor
  ) throws CommandLineArgumentException {
    GradleCommandLine commandLine = GradleCommandLine.parse(fullCommandLine);
    ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
    settings.setExternalProjectPath(projectPath);
    settings.setTaskNames(commandLine.getTasks().getTokens());
    settings.setScriptParameters(commandLine.getOptions().getText());
    settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.toString());
    return new ExternalTaskExecutionInfo(settings, executor == null ? DefaultRunExecutor.EXECUTOR_ID : executor.getId());
  }
}
