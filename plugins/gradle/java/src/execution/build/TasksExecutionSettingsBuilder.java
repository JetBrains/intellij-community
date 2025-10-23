// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build;

import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.text.Strings;
import com.intellij.task.*;
import com.intellij.task.impl.JpsProjectTaskRunner;
import com.intellij.util.CommonProcessors;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.MultiMap;
import org.gradle.api.internal.jvm.ClassDirectoryBinaryNamingScheme;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.GradleInitScriptUtil;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;
import org.jetbrains.plugins.gradle.service.task.VersionSpecificInitScript;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleModuleData;

import java.util.*;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.*;
import static com.intellij.openapi.util.text.StringUtil.*;
import static org.jetbrains.plugins.gradle.execution.GradleRunnerUtil.resolveProjectPath;

@ApiStatus.Internal
public class TasksExecutionSettingsBuilder {
  @SuppressWarnings({"GrUnresolvedAccess", "GroovyAssignabilityCheck"})
  @Language("Groovy")
  private static final String FORCE_COMPILE_TASKS_INIT_SCRIPT_TEMPLATE = """
    projectsEvaluated {\s
      rootProject.findProject('%s')?.tasks?.withType(AbstractCompile) { \s
        outputs.upToDateWhen { false }\s
      }\s
    }
    """;

  private final MultiMap<String, String> buildTasksMap = MultiMap.createLinkedSet();
  private final MultiMap<String, String> initScripts = MultiMap.createLinkedSet();
  private final MultiMap<String, VersionSpecificInitScript> versionedInitScripts = MultiMap.createLinkedSet();

  private final List<Module> modulesToBuild;
  private final List<Module> modulesOfResourcesToBuild;
  private final List<Module> modulesOfFiles;

  public TasksExecutionSettingsBuilder(@NotNull List<ProjectTask> tasks) {
    Map<Class<? extends ProjectTask>, List<ProjectTask>> taskMap = JpsProjectTaskRunner.groupBy(tasks);
    modulesToBuild = addModulesBuildTasks(taskMap.get(ModuleBuildTask.class));
    modulesOfResourcesToBuild = addModulesBuildTasks(taskMap.get(ModuleResourcesBuildTask.class));
    modulesOfFiles = addModulesBuildTasks(taskMap.get(ModuleFilesBuildTask.class));
    addArtifactsBuildTasks(taskMap.get(ProjectModelBuildTask.class));
  }

  public Set<String> getRootPaths() {
    return buildTasksMap.keySet();
  }

  public List<Module> getAffectedModules() {
    return ContainerUtil.concat(modulesToBuild, modulesOfResourcesToBuild, modulesOfFiles);
  }

  public void addInitScripts(String rootProjectPath, Iterable<VersionSpecificInitScript> initScript) {
    Collection<VersionSpecificInitScript> versionSpecificInitScripts = versionedInitScripts.getModifiable(rootProjectPath);
    ContainerUtil.addAll(versionSpecificInitScripts, initScript);
  }

  public Collection<VersionSpecificInitScript> getVersionedInitScripts(String rootProjectPath) {
    return versionedInitScripts.get(rootProjectPath);
  }

  public String getInitScript(String rootProjectPath) {
    return GradleInitScriptUtil.joinInitScripts(initScripts.get(rootProjectPath));
  }

  public List<String> getTasksToExecute(String rootProjectPath) {
    return new ArrayList<>(buildTasksMap.get(rootProjectPath));
  }

  private List<Module> addModulesBuildTasks(@Nullable Collection<? extends ProjectTask> projectTasks) {
    if (ContainerUtil.isEmpty(projectTasks)) return Collections.emptyList();

    List<Module> affectedModules = new SmartList<>();
    Map<Module, String> rootPathsMap = FactoryMap.create(module -> notNullize(resolveProjectPath(module)));
    for (ProjectTask projectTask : projectTasks) {
      if (!(projectTask instanceof ModuleBuildTask moduleBuildTask)) continue;

      collectAffectedModules(affectedModules, moduleBuildTask);

      Module module = moduleBuildTask.getModule();
      String rootProjectPath = rootPathsMap.get(module);
      if (isEmpty(rootProjectPath)) continue;

      final String projectId = getExternalProjectId(module);
      if (projectId == null) continue;
      String externalProjectPath = getExternalProjectPath(module);
      if (externalProjectPath == null) continue;

      GradleModuleData gradleModuleData = CachedModuleDataFinder.getGradleModuleData(module);
      if (gradleModuleData == null) continue;
      String gradlePath = gradleModuleData.getGradlePathOrNull();
      if (gradlePath == null) continue;
      String gradleIdentityPath = gradleModuleData.getGradleIdentityPathOrNull();
      if (gradleIdentityPath == null) continue;

      boolean isGradleProjectDirUsedToRunTasks = gradleModuleData.getDirectoryToRunTask().equals(gradleModuleData.getGradleProjectDir());
      if (!isGradleProjectDirUsedToRunTasks) {
        rootProjectPath = gradleModuleData.getDirectoryToRunTask();
      }

      List<TaskData> taskDataList =
        ContainerUtil.mapNotNull(gradleModuleData.findAll(ProjectKeys.TASK), taskData -> taskData.isInherited() ? null : taskData);
      if (taskDataList.isEmpty()) continue;

      String taskPathPrefix = trimEnd(gradleIdentityPath, ":") + ":";
      List<String> gradleModuleTasks = ContainerUtil.map(taskDataList, data -> trimStart(data.getName(), taskPathPrefix));

      Collection<String> projectInitScripts = initScripts.getModifiable(rootProjectPath);
      Collection<String> buildRootTasks = buildTasksMap.getModifiable(rootProjectPath);
      final String moduleType = getExternalModuleType(module);

      if (!moduleBuildTask.isIncrementalBuild() && !(moduleBuildTask instanceof ModuleFilesBuildTask)) {
        projectInitScripts.add(String.format(FORCE_COMPILE_TASKS_INIT_SCRIPT_TEMPLATE, gradlePath));
      }
      if (moduleBuildTask.isIncludeRuntimeDependencies()) {
        // if runtime deps are required, force Gradle to process all resources
        projectInitScripts.add("System.setProperty('org.gradle.java.compile-classpath-packaging', 'true')\n");
      }

      String assembleTask = "assemble";
      boolean buildOnlyResources = projectTask instanceof ModuleResourcesBuildTask;
      String buildTaskPrefix = buildOnlyResources ? "process" : "";
      String buildTaskSuffix = buildOnlyResources ? "resources" : "classes";
      if (GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY.equals(moduleType)) {
        String sourceSetName = GradleProjectResolverUtil.getSourceSetName(module);
        String gradleTask = getTaskName(buildTaskPrefix, buildTaskSuffix, sourceSetName);
        if (!addIfContains(taskPathPrefix, gradleTask, gradleModuleTasks, buildRootTasks) &&
            ("main".equals(sourceSetName) || "test".equals(sourceSetName))) {
            buildRootTasks.add(taskPathPrefix + assembleTask);
        }
        for (GradleBuildTasksProvider buildTasksProvider : GradleBuildTasksProvider.EP_NAME.getExtensions()) {
          if (buildTasksProvider.isApplicable(moduleBuildTask)) {
            buildTasksProvider.addBuildTasks(
              moduleBuildTask,
              task -> buildTasksMap.putValue(task.getLinkedExternalProjectPath(), task.getName()),
              (String path, VersionSpecificInitScript script) -> versionedInitScripts.putValue(path, script)
            );
          }
        }
      }
      else {
        String gradleTask = getTaskName(buildTaskPrefix, buildTaskSuffix, null);
        if (addIfContains(taskPathPrefix, gradleTask, gradleModuleTasks, buildRootTasks)) {
          String gradleTestTask = getTaskName(buildTaskPrefix, buildTaskSuffix, "test");
          addIfContains(taskPathPrefix, gradleTestTask, gradleModuleTasks, buildRootTasks);
        }
        else if (gradleModuleTasks.contains(assembleTask)) {
          buildRootTasks.add(taskPathPrefix + assembleTask);
        }
      }
    }
    return affectedModules;
  }

  private static @NotNull String getTaskName(@NotNull String taskPrefix, @NotNull String taskSuffix, @Nullable String sourceSetName) {
    if (Strings.isEmpty(sourceSetName)) sourceSetName = "main";
    return new ClassDirectoryBinaryNamingScheme(sourceSetName).getTaskName(taskPrefix, taskSuffix);
  }

  private void addArtifactsBuildTasks(@Nullable Collection<? extends ProjectTask> tasks) {
    if (ContainerUtil.isEmpty(tasks)) return;

    for (ProjectTask projectTask : tasks) {
      if (!(projectTask instanceof ProjectModelBuildTask<?> projectModelBuildTask)) continue;

      for (GradleBuildTasksProvider buildTasksProvider : GradleBuildTasksProvider.EP_NAME.getExtensions()) {
        if (buildTasksProvider.isApplicable(projectModelBuildTask)) {
          buildTasksProvider.addBuildTasks(
            projectModelBuildTask,
            task -> buildTasksMap.putValue(task.getLinkedExternalProjectPath(), task.getName()),
            (String path, VersionSpecificInitScript script) -> versionedInitScripts.putValue(path, script)
          );
        }
      }
    }
  }

  private static boolean addIfContains(@NotNull String taskPathPrefix,
                                       @NotNull String gradleTask,
                                       @NotNull List<String> moduleTasks,
                                       @NotNull Collection<String> buildRootTasks) {
    if (moduleTasks.contains(gradleTask)) {
      buildRootTasks.add(taskPathPrefix + gradleTask);
      return true;
    }
    return false;
  }

  private static void collectAffectedModules(@NotNull List<Module> affectedModules, @NotNull ModuleBuildTask moduleBuildTask) {
    Module module = moduleBuildTask.getModule();
    if (moduleBuildTask.isIncludeDependentModules()) {
      OrderEnumerator enumerator = ModuleRootManager.getInstance(module).orderEntries().recursively();
      if (!moduleBuildTask.isIncludeRuntimeDependencies()) {
        enumerator = enumerator.compileOnly();
      }
      enumerator.forEachModule(new CommonProcessors.CollectProcessor<>(affectedModules));
    }
    else {
      affectedModules.add(module);
    }
  }
}
