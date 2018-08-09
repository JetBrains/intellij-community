// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.Executor;
import com.intellij.ide.actions.runAnything.activity.RunAnythingProviderBase;
import com.intellij.ide.actions.runAnything.items.RunAnythingHelpItem;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
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

    appendProjectsVariants(result, dataContext, prefix);
    if (!result.isEmpty()) return result;

    appendArgumentsVariants(result, prefix, toComplete);
    if (!result.isEmpty()) return result;

    appendTasksVariants(result, prefix, dataContext);
    return result;
  }

  private void appendProjectsVariants(@NotNull List<String> result,
                                      @NotNull DataContext dataContext,
                                      @NotNull String prefix) {
    if (!prefix.trim().equals(getHelpCommand())) return;

    Project project = fetchProject(dataContext);
    Collection<GradleProjectSettings> projectsSettings = GradleSettings.getInstance(project).getLinkedProjectsSettings();
    if (projectsSettings.isEmpty()) return;

    ProjectDataManager dataManager = ProjectDataManager.getInstance();
    projectsSettings.stream()
      .map(setting -> dataManager.getExternalProjectData(project, GradleConstants.SYSTEM_ID, setting.getExternalProjectPath()))
      .filter(projectInfo -> projectInfo != null && projectInfo.getExternalProjectStructure() != null)
      .map(projectInfo -> projectInfo.getExternalProjectStructure().getData())
      .forEach(data -> result.add(prefix + data.getExternalName()));
  }

  private void appendTasksVariants(@NotNull List<String> result, @NotNull String prefix, @NotNull DataContext dataContext) {
    Project project = fetchProject(dataContext);
    String commandLine = StringUtil.trimStart(prefix, getHelpCommand()).trim();
    ProjectData projectData = getProjectData(project, commandLine);
    if (projectData == null) return;

    MultiMap<String, String> tasks = fetchTasks(dataContext).get(projectData);
    if (tasks == null) return;

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
    String commandLine = StringUtil.trimStart(value, getHelpCommand()).trim();

    ProjectData projectData = getProjectData(project, commandLine);
    if (projectData == null) return;

    commandLine = StringUtil.trimStart(commandLine, projectData.getExternalName() + " ");
    Executor executor = EXECUTOR_KEY.getData(dataContext);
    GradleExecuteTaskAction.runGradle(project, executor, projectData.getLinkedExternalProjectPath(), commandLine);
  }

  @Nullable
  private static ProjectData getProjectData(@NotNull Project project, @NotNull String commandLine) {
    Collection<GradleProjectSettings> projectsSettings = GradleSettings.getInstance(project).getLinkedProjectsSettings();
    if (projectsSettings.isEmpty()) return null;

    ProjectDataManager dataManager = ProjectDataManager.getInstance();
    return projectsSettings.stream()
      .map(setting -> dataManager.getExternalProjectData(project, GradleConstants.SYSTEM_ID, setting.getExternalProjectPath()))
      .filter(projectInfo -> projectInfo != null && projectInfo.getExternalProjectStructure() != null)
      .map(projectInfo -> projectInfo.getExternalProjectStructure().getData())
      .filter(projectData -> projectsSettings.size() == 1 || StringUtil.startsWith(commandLine, projectData.getExternalName()))
      .findFirst().orElse(null);
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

  @Nullable
  @Override
  public RunAnythingHelpItem getHelpItem(@NotNull DataContext dataContext) {
    String placeholder = getHelpCommandPlaceholder(dataContext);
    String commandPrefix = getHelpCommand();
    return new RunAnythingHelpItem(placeholder, commandPrefix, getHelpDescription(), getHelpIcon());
  }

  @NotNull
  public String getCompletionGroupTitle() {
    return "Gradle tasks";
  }

  @NotNull
  @Override
  public String getHelpCommandPlaceholder() {
    return getHelpCommandPlaceholder(null);
  }

  @NotNull
  public String getHelpCommandPlaceholder(@Nullable DataContext dataContext) {
    if (dataContext != null) {
      Project project = fetchProject(dataContext);
      if (GradleSettings.getInstance(project).getLinkedProjectsSettings().size() > 1) {
        return "gradle <rootProjectName> <taskName...> <--option-name...>";
      }
    }
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

  private static Map<ProjectData, MultiMap<String, String>> fetchTasks(@NotNull DataContext dataContext) {
    Project project = fetchProject(dataContext);
    return CachedValuesManager.getManager(project).getCachedValue(
      project, () -> CachedValueProvider.Result.create(getTasksMap(project), ProjectRootManager.getInstance(project)));
  }

  @NotNull
  private static Map<ProjectData, MultiMap<String, String>> getTasksMap(Project project) {
    Map<ProjectData, MultiMap<String, String>> tasks = ContainerUtil.newLinkedHashMap();
    for (GradleProjectSettings setting : GradleSettings.getInstance(project).getLinkedProjectsSettings()) {
      final ExternalProjectInfo projectData =
        ProjectDataManager.getInstance().getExternalProjectData(project, GradleConstants.SYSTEM_ID, setting.getExternalProjectPath());
      if (projectData == null || projectData.getExternalProjectStructure() == null) continue;

      MultiMap<String, String> projectTasks = MultiMap.createOrderedSet();
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
            projectTasks.putValue(taskPathPrefix, taskName);
          }
        }
      }
      tasks.put(projectData.getExternalProjectStructure().getData(), projectTasks);
    }
    return tasks;
  }
}
