/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.execution.build;

import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.compiler.ex.CompilerPathsEx;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.task.*;
import com.intellij.task.impl.InternalProjectTaskRunner;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.settings.GradleSystemRunningSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TODO automatically create exploded-war task
 * task explodedWar(type: Copy) {
 * into "$buildDir/explodedWar"
 * with war
 * }
 *
 * @author Vladislav.Soroka
 * @since 5/11/2016
 */
public class GradleProjectTaskRunner extends ProjectTaskRunner {
  @Override
  public void run(@NotNull Project project,
                  @NotNull ProjectTaskContext context,
                  @Nullable ProjectTaskNotification callback,
                  @NotNull Collection<? extends ProjectTask> tasks) {
    String executionName = "Gradle build";

    MultiMap<String, String> buildTasksMap = MultiMap.createLinkedSet();
    MultiMap<String, String> cleanTasksMap = MultiMap.createLinkedSet();

    Map<Class<? extends ProjectTask>, List<ProjectTask>> taskMap = InternalProjectTaskRunner.groupBy(tasks);

    List<Module> modules = addModulesBuildTasks(taskMap.get(ModuleBuildTask.class), cleanTasksMap, buildTasksMap);
    // TODO there should be 'gradle' way to build files instead of related modules entirely
    List<Module> modulesOfFiles = addModulesBuildTasks(taskMap.get(ModuleFilesBuildTask.class), cleanTasksMap, buildTasksMap);
    addArtifactsBuildTasks(taskMap.get(ArtifactBuildTask.class), cleanTasksMap, buildTasksMap);

    // TODO send a message if nothing to build
    Set<String> rootPaths = buildTasksMap.keySet();
    AtomicInteger successCounter = new AtomicInteger();
    AtomicInteger errorCounter = new AtomicInteger();

    TaskCallback taskCallback = callback == null ? null : new TaskCallback() {
      @Override
      public void onSuccess() {
        handle(true);
      }

      @Override
      public void onFailure() {
        handle(false);
      }

      private void handle(boolean success) {
        int successes = success ? successCounter.incrementAndGet() : successCounter.get();
        int errors = success ? errorCounter.get() : errorCounter.incrementAndGet();
        if (successes + errors == rootPaths.size()) {
          if (!project.isDisposed()) {
            // refresh on output roots is required in order for the order enumerator to see all roots via VFS
            final List<Module> affectedModules = ContainerUtil.concat(modules, modulesOfFiles);
            // have to refresh in case of errors too, because run configuration may be set to ignore errors
            Collection<String> affectedRoots = ContainerUtil.newHashSet(
              CompilerPathsEx.getOutputPaths(ContainerUtil.toArray(affectedModules, new Module[affectedModules.size()])));
            if (!affectedRoots.isEmpty()) {
              CompilerUtil.refreshOutputRoots(affectedRoots);
            }
          }
          callback.finished(new ProjectTaskResult(false, errors, 0));
        }
      }
    };

    String gradleVmOptions = GradleSettings.getInstance(project).getGradleVmOptions();
    for (String rootProjectPath : rootPaths) {
      Collection<String> buildTasks = buildTasksMap.get(rootProjectPath);
      if (buildTasks.isEmpty()) continue;
      Collection<String> cleanTasks = cleanTasksMap.get(rootProjectPath);

      ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
      settings.setExecutionName(executionName);
      settings.setExternalProjectPath(rootProjectPath);
      settings.setTaskNames(ContainerUtil.collect(ContainerUtil.concat(cleanTasks, buildTasks).iterator()));
      //settings.setScriptParameters(scriptParameters);
      settings.setVmOptions(gradleVmOptions);
      settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.getId());
      ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID,
                                 taskCallback, ProgressExecutionMode.IN_BACKGROUND_ASYNC, false);
    }
  }

  @Override
  public boolean canRun(@NotNull ProjectTask projectTask) {
    if (!GradleSystemRunningSettings.getInstance().isUseGradleAwareMake()) return false;
    if (projectTask instanceof ModuleBuildTask) {
      return ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, ((ModuleBuildTask)projectTask).getModule());
    }
    if (projectTask instanceof ArtifactBuildTask) {
      ArtifactBuildTask artifactBuildTask = (ArtifactBuildTask)projectTask;
      for (GradleArtifactBuildTasksProvider buildTasksProvider : GradleArtifactBuildTasksProvider.EP_NAME.getExtensions()) {
        if (buildTasksProvider.isApplicable(artifactBuildTask)) return true;
      }
    }

    if (projectTask instanceof ExecuteRunConfigurationTask) {
      RunProfile runProfile = ((ExecuteRunConfigurationTask)projectTask).getRunProfile();
      if (runProfile instanceof ModuleBasedConfiguration) {
        RunConfigurationModule module = ((ModuleBasedConfiguration)runProfile).getConfigurationModule();
        if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module.getModule())) {
          return false;
        }
      }
      for (GradleExecutionEnvironmentProvider environmentProvider : GradleExecutionEnvironmentProvider.EP_NAME.getExtensions()) {
        if (environmentProvider.isApplicable(((ExecuteRunConfigurationTask)projectTask))) {
          return true;
        }
      }
    }
    return false;
  }


  @Override
  public ExecutionEnvironment createExecutionEnvironment(@NotNull Project project,
                                                         @NotNull ExecuteRunConfigurationTask task,
                                                         @Nullable Executor executor) {
    for (GradleExecutionEnvironmentProvider environmentProvider : GradleExecutionEnvironmentProvider.EP_NAME.getExtensions()) {
      if (environmentProvider.isApplicable(task)) {
        return environmentProvider.createExecutionEnvironment(project, task, executor);
      }
    }
    return null;
  }

  private static List<Module> addModulesBuildTasks(@Nullable Collection<? extends ProjectTask> projectTasks,
                                                         @NotNull MultiMap<String, String> cleanTasksMap,
                                                         @NotNull MultiMap<String, String> buildTasksMap) {
    if (ContainerUtil.isEmpty(projectTasks)) return Collections.emptyList();

    List<Module> affectedModules = new SmartList<>();
    final CachedModuleDataFinder moduleDataFinder = new CachedModuleDataFinder();
    for (ProjectTask projectTask : projectTasks) {
      if (!(projectTask instanceof ModuleBuildTask)) continue;

      ModuleBuildTask moduleBuildTask = (ModuleBuildTask)projectTask;
      Module module = moduleBuildTask.getModule();
      affectedModules.add(module);

      final String rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
      if (rootProjectPath == null) continue;

      final String projectId = ExternalSystemApiUtil.getExternalProjectId(module);
      if (projectId == null) continue;
      final String externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
      if (externalProjectPath == null || StringUtil.endsWith(externalProjectPath, "buildSrc")) continue;

      final DataNode<? extends ModuleData> moduleDataNode = moduleDataFinder.findMainModuleData(module);
      if (moduleDataNode == null) continue;

      List<String> gradleTasks = ContainerUtil.mapNotNull(ExternalSystemApiUtil.findAll(moduleDataNode, ProjectKeys.TASK),
                                                    node -> node.getData().isInherited() ? null : node.getData().getName());

      Collection<String> cleanRootTasks = cleanTasksMap.getModifiable(rootProjectPath);
      Collection<String> buildRootTasks = buildTasksMap.getModifiable(rootProjectPath);
      final String moduleType = ExternalSystemApiUtil.getExternalModuleType(module);
      String gradlePath = GradleProjectResolverUtil.getGradlePath(module);
      if(gradlePath == null) continue;
      if(gradlePath.equals(":")) {
        gradlePath = "";
      }

      if (GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY.equals(moduleType)) {
        String sourceSetName = GradleProjectResolverUtil.getSourceSetName(module);
        String gradleTask = StringUtil.isEmpty(sourceSetName) || "main".equals(sourceSetName) ? "classes" : sourceSetName + "Classes";
        if (gradleTasks.contains(gradleTask)) {
          if (!moduleBuildTask.isIncrementalBuild()) {
            cleanRootTasks.add(gradlePath + ":clean" + StringUtil.capitalize(gradleTask));
          }
          buildRootTasks.add(gradlePath + ":" + gradleTask);
        }
        else if ("main".equals(sourceSetName) || "test".equals(sourceSetName)) {
          if (!moduleBuildTask.isIncrementalBuild()) {
            cleanRootTasks.add(gradlePath + ":clean");
          }
          buildRootTasks.add(gradlePath + ":build");
        }
      }
      else {
        if (!moduleBuildTask.isIncrementalBuild()) {
          if (gradleTasks.contains("classes")) {
            cleanRootTasks.add((StringUtil.equals(rootProjectPath, externalProjectPath) ? ":cleanClasses" : gradlePath + ":cleanClasses"));
            cleanRootTasks.add((StringUtil.equals(rootProjectPath, externalProjectPath) ? ":cleanTestClasses" : gradlePath + ":cleanTestClasses"));
          }
          else if (gradleTasks.contains("clean")) {
            cleanRootTasks.add((StringUtil.equals(rootProjectPath, externalProjectPath) ? "clean" : gradlePath + ":clean"));
          }
          else {
            cleanTasksMap.getModifiable(externalProjectPath).add("clean");
          }
        }
        if (gradleTasks.contains("classes")) {
          buildRootTasks.add((StringUtil.equals(rootProjectPath, externalProjectPath) ? ":classes" : gradlePath + ":classes"));
          buildRootTasks.add((StringUtil.equals(rootProjectPath, externalProjectPath) ? ":testClasses" : gradlePath + ":testClasses"));
        }
        else if (gradleTasks.contains("build")) {
          buildRootTasks.add((StringUtil.equals(rootProjectPath, externalProjectPath) ? "build" : gradlePath + ":build"));
        }
        else {
          buildTasksMap.getModifiable(externalProjectPath).add("build");
        }
      }
    }
    return affectedModules;
  }

  private static void addArtifactsBuildTasks(@Nullable Collection<? extends ProjectTask> tasks,
                                             @NotNull MultiMap<String, String> cleanTasksMap,
                                             @NotNull MultiMap<String, String> buildTasksMap) {
    if (ContainerUtil.isEmpty(tasks)) return;

    for (ProjectTask projectTask : tasks) {
      if (!(projectTask instanceof ArtifactBuildTask)) continue;

      ArtifactBuildTask artifactBuildTask = (ArtifactBuildTask)projectTask;
        for (GradleArtifactBuildTasksProvider buildTasksProvider : GradleArtifactBuildTasksProvider.EP_NAME.getExtensions()) {
          if (buildTasksProvider.isApplicable(artifactBuildTask)) {
            buildTasksProvider.addArtifactsTargetsBuildTasks(
              artifactBuildTask,
              task -> cleanTasksMap.putValue(task.getLinkedExternalProjectPath(), task.getName()),
              task -> buildTasksMap.putValue(task.getLinkedExternalProjectPath(), task.getName())
            );
          }
        }
      }
  }
}
