// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.Executor;
import com.intellij.ide.actions.runAnything.activity.RunAnythingProviderBase;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import groovyjarjarcommonscli.Option;
import icons.GradleIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.action.GradleExecuteTaskAction;
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.ide.actions.runAnything.RunAnythingAction.EXECUTOR_KEY;
import static com.intellij.ide.actions.runAnything.RunAnythingUtil.fetchProject;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getChildren;

/**
 * @author Vladislav.Soroka
 */
public class GradleRunAnythingProvider extends RunAnythingProviderBase<String> {

  @NotNull
  @Override
  public RunAnythingItem getMainListItem(@NotNull DataContext dataContext, @NotNull String value) {
    return new RunAnythingGradleItem(getCommand(value), getIcon(value));
  }

  @Nullable
  @Override
  public String findMatchingValue(@NotNull DataContext dataContext, @NotNull String pattern) {
    return pattern.startsWith(getHelpCommand()) ? getCommand(pattern) : null;
  }

  @NotNull
  @Override
  public Collection<String> getValues(@NotNull DataContext dataContext, @NotNull String pattern) {
    if (!pattern.startsWith(getHelpCommand())) {
      return Collections.emptyList();
    }
    List<String> result = ContainerUtil.newSmartList();

    int spaceIndex = StringUtil.lastIndexOf(pattern, ' ', getHelpCommand().length(), pattern.length());
    String prefix = StringUtil.trim(spaceIndex < 0 ? getHelpCommand() : pattern.substring(0, spaceIndex + 1)) + ' ';
    String toComplete = spaceIndex < 0 ? "" : pattern.substring(spaceIndex + 1);

    appendArgumentsVariants(result, prefix, toComplete);
    if (!result.isEmpty()) return result;

    appendTasksVariants(result, prefix, dataContext);
    return result;
  }

  private static void appendTasksVariants(@NotNull List<String> result, @NotNull String prefix, @NotNull DataContext dataContext) {
    MultiMap<String, String> tasks = fetchTasks(dataContext);
    for (Map.Entry<String, Collection<String>> entry : tasks.entrySet()) {
      for (String taskName : entry.getValue()) {
        String taskFqn = entry.getKey() + taskName;
        result.add(prefix + taskFqn);
      }
    }
  }

  private static void appendArgumentsVariants(@NotNull List<String> result, @NotNull String prefix, @NotNull String toComplete) {
    if (!toComplete.startsWith("-")) return;

    boolean isLongOpt = toComplete.startsWith("--");
    prefix += (isLongOpt ? "--" : "-");
    for (Object option : GradleCommandLineOptionsProvider.getSupportedOptions().getOptions()) {
      if (option instanceof Option) {
        String opt = isLongOpt ? ((Option)option).getLongOpt() : ((Option)option).getOpt();
        if (StringUtil.isNotEmpty(opt)) {
          result.add(prefix + opt);
        }
      }
    }
  }

  @Override
  public void execute(@NotNull DataContext dataContext, @NotNull String value) {
    Project project = fetchProject(dataContext);
    String commandLine = StringUtil.trimStart(value, getHelpCommand());
    Executor executor = EXECUTOR_KEY.getData(dataContext);
    for (GradleProjectSettings setting : GradleSettings.getInstance(project).getLinkedProjectsSettings()) {
      GradleExecuteTaskAction.runGradle(project, executor, setting.getExternalProjectPath(), commandLine);
    }
  }

  @NotNull
  @Override
  public String getCommand(@NotNull String value) {
    return value;
  }

  @Nullable
  @Override
  public Icon getIcon(@NotNull String value) {
    return GradleIcons.Gradle;
  }

  @NotNull
  public String getCompletionGroupTitle() {
    return "Gradle tasks";
  }

  @NotNull
  @Override
  public String getHelpCommandPlaceholder() {
    return "gradle <taskName...> <--option-name...>";
  }

  @NotNull
  @Override
  public String getHelpCommand() {
    return "gradle";
  }

  @Override
  public Icon getHelpIcon() {
    return GradleIcons.Gradle;
  }

  private static MultiMap<String, String> fetchTasks(@NotNull DataContext dataContext) {
    Project project = fetchProject(dataContext);
    return CachedValuesManager.getManager(project).getCachedValue(
      project, () -> CachedValueProvider.Result.create(getTasksMap(project), ProjectRootManager.getInstance(project)));
  }

  @NotNull
  private static MultiMap<String, String> getTasksMap(Project project) {
    MultiMap<String, String> tasks = MultiMap.createOrderedSet();
    for (GradleProjectSettings setting : GradleSettings.getInstance(project).getLinkedProjectsSettings()) {
      final ExternalProjectInfo projectData =
        ProjectDataManager.getInstance().getExternalProjectData(project, GradleConstants.SYSTEM_ID, setting.getExternalProjectPath());
      if (projectData == null || projectData.getExternalProjectStructure() == null) continue;

      for (DataNode<ModuleData> moduleDataNode : getChildren(projectData.getExternalProjectStructure(), ProjectKeys.MODULE)) {
        String gradlePath;
        String moduleId = moduleDataNode.getData().getId();
        if (moduleId.charAt(0) != ':') {
          int colonIndex = moduleId.indexOf(':');
          gradlePath = colonIndex > 0 ? moduleId.substring(colonIndex) : ":";
        }
        else {
          gradlePath = moduleId;
        }
        for (DataNode<TaskData> node : getChildren(moduleDataNode, ProjectKeys.TASK)) {
          TaskData taskData = node.getData();
          String taskName = taskData.getName();
          if (StringUtil.isNotEmpty(taskName)) {
            String taskPathPrefix = ":".equals(gradlePath) || taskName.startsWith(gradlePath) ? "" : (gradlePath + ':');
            tasks.putValue(taskPathPrefix, taskName);
          }
        }
      }
    }
    return tasks;
  }
}
