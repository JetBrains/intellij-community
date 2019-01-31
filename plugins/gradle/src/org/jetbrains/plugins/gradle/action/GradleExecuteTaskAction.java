// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.action;

import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.externalSystem.action.ExternalSystemAction;
import com.intellij.openapi.externalSystem.action.ExternalSystemActionUtil;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.model.project.ExternalConfigPathAware;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.view.ExternalProjectsView;
import com.intellij.openapi.externalSystem.view.ExternalSystemNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.execution.ParametersListUtil;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsConverter;
import org.jetbrains.plugins.gradle.service.task.ExecuteGradleTaskHistoryService;
import org.jetbrains.plugins.gradle.service.task.GradleRunTaskDialog;
import org.jetbrains.plugins.gradle.statistics.GradleActionsUsagesCollector;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 */
public class GradleExecuteTaskAction extends ExternalSystemAction {

  @Override
  protected boolean isVisible(@NotNull AnActionEvent e) {
    if (!super.isVisible(e)) return false;
    final ExternalProjectsView projectsView = ExternalSystemDataKeys.VIEW.getData(e.getDataContext());
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
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    GradleActionsUsagesCollector.trigger(project, this, e);
    ExecuteGradleTaskHistoryService historyService = ExecuteGradleTaskHistoryService.getInstance(project);
    GradleRunTaskDialog dialog = new GradleRunTaskDialog(project, historyService.getHistory());
    String lastWorkingDirectory = historyService.getWorkDirectory();
    if (lastWorkingDirectory.length() == 0) {
      lastWorkingDirectory = obtainAppropriateWorkingDirectory(e);
    }

    dialog.setWorkDirectory(lastWorkingDirectory);

    if (StringUtil.isEmptyOrSpaces(historyService.getCanceledCommand())) {
      if (historyService.getHistory().size() > 0) {
        dialog.setCommandLine(historyService.getHistory().get(0));
      }
    }
    else {
      dialog.setCommandLine(historyService.getCanceledCommand());
    }

    if (!dialog.showAndGet()) {
      historyService.setCanceledCommand(dialog.getCommandLine());
      return;
    }

    historyService.setCanceledCommand(null);

    String fullCommandLine = dialog.getCommandLine();
    fullCommandLine = fullCommandLine.trim();

    String workDirectory = dialog.getWorkDirectory();

    historyService.addCommand(fullCommandLine, workDirectory);

    runGradle(project, null, workDirectory, fullCommandLine);
  }

  public static void runGradle(@NotNull Project project,
                               @Nullable Executor executor,
                               @NotNull String workDirectory,
                               @NotNull String fullCommandLine) {
    final ExternalTaskExecutionInfo taskExecutionInfo;
    try {
      taskExecutionInfo = buildTaskInfo(workDirectory, fullCommandLine, executor);
    }
    catch (CommandLineArgumentException ex) {
      final NotificationData notificationData = new NotificationData(
        "<b>Command-line arguments cannot be parsed</b>",
        "<i>" + fullCommandLine + "</i> \n" + ex.getMessage(),
        NotificationCategory.WARNING, NotificationSource.TASK_EXECUTION
      );
      notificationData.setBalloonNotification(true);
      ExternalSystemNotificationManager.getInstance(project).showNotification(GradleConstants.SYSTEM_ID, notificationData);
      return;
    }

    ExternalSystemUtil.runTask(taskExecutionInfo.getSettings(), taskExecutionInfo.getExecutorId(), project, GradleConstants.SYSTEM_ID);

    RunnerAndConfigurationSettings configuration =
      ExternalSystemUtil.createExternalSystemRunnerAndConfigurationSettings(taskExecutionInfo.getSettings(),
                                                                            project, GradleConstants.SYSTEM_ID);
    if (configuration == null) return;

    RunManager runManager = RunManager.getInstance(project);
    final RunnerAndConfigurationSettings existingConfiguration = runManager.findConfigurationByTypeAndName(configuration.getType(), configuration.getName());
    if (existingConfiguration == null) {
      runManager.setTemporaryConfiguration(configuration);
    }
    else {
      runManager.setSelectedConfiguration(existingConfiguration);
    }
  }

  private static ExternalTaskExecutionInfo buildTaskInfo(@NotNull String projectPath,
                                                         @NotNull String fullCommandLine,
                                                         @Nullable Executor executor)
    throws CommandLineArgumentException {
    CommandLineParser gradleCmdParser = new CommandLineParser();

    GradleCommandLineOptionsConverter commandLineConverter = new GradleCommandLineOptionsConverter();
    commandLineConverter.configure(gradleCmdParser);
    ParsedCommandLine parsedCommandLine = gradleCmdParser.parse(ParametersListUtil.parse(fullCommandLine, true));

    final Map<String, List<String>> optionsMap =
      commandLineConverter.convert(parsedCommandLine, new HashMap<>());

    final List<String> systemProperties = optionsMap.remove("system-prop");
    final String vmOptions = systemProperties == null ? "" : StringUtil.join(systemProperties, entry -> "-D" + entry, " ");

    final String scriptParameters = StringUtil.join(optionsMap.entrySet(), entry -> {
      final List<String> values = entry.getValue();
      final String longOptionName = entry.getKey();
      if (values != null && !values.isEmpty()) {
        return StringUtil.join(values, entry1 -> "--" + longOptionName + ' ' + entry1, " ");
      }
      else {
        return "--" + longOptionName;
      }
    }, " ");

    final List<String> tasks = parsedCommandLine.getExtraArguments();

    ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
    settings.setExternalProjectPath(projectPath);
    settings.setTaskNames(tasks);
    settings.setScriptParameters(scriptParameters);
    settings.setVmOptions(vmOptions);
    settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.toString());
    return new ExternalTaskExecutionInfo(settings, executor == null ? DefaultRunExecutor.EXECUTOR_ID : executor.getId());
  }

  private static String obtainAppropriateWorkingDirectory(AnActionEvent e) {
    final List<ExternalSystemNode> selectedNodes = ExternalSystemDataKeys.SELECTED_NODES.getData(e.getDataContext());
    if (selectedNodes == null || selectedNodes.size() != 1) {
      final Module module = ExternalSystemActionUtil.getModule(e.getDataContext());
      String projectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
      return projectPath == null ? "" : projectPath;
    }

    final ExternalSystemNode<?> node = selectedNodes.get(0);
    final Object externalData = node.getData();
    if (externalData instanceof ExternalConfigPathAware) {
      return ((ExternalConfigPathAware)externalData).getLinkedExternalProjectPath();
    }
    else {
      final ExternalConfigPathAware parentExternalConfigPathAware = node.findParentData(ExternalConfigPathAware.class);
      return parentExternalConfigPathAware != null ? parentExternalConfigPathAware.getLinkedExternalProjectPath() : "";
    }
  }
}
