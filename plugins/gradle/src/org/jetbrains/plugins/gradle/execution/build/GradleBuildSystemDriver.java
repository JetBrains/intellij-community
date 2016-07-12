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

import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.build.*;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.settings.GradleSystemRunningSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
public class GradleBuildSystemDriver extends BuildSystemDriver {
  @Override
  public void build(@NotNull BuildContext buildContext, @Nullable BuildChunkStatusNotification buildCallback) {
    String executionName = null;
    if (buildContext.getScope() instanceof ProjectBuildScope) {
      executionName = buildContext.isIncrementalBuild() ? "Make" : "Rebuild";
    }

    MultiMap<String, String> buildTasksMap = MultiMap.createLinkedSet();
    MultiMap<String, String> cleanTasksMap = MultiMap.createLinkedSet();

    Map<Class<? extends BuildTarget>, List<BuildTarget>> targetsMap =
      buildContext.getScope().getTargets().stream().collect(Collectors.groupingBy(BuildTarget::getClass));
    addModulesTargetsBuildTasks(buildContext, targetsMap, cleanTasksMap, buildTasksMap);
    addFilesTargetsBuildTasks(buildContext, targetsMap, cleanTasksMap, buildTasksMap);
    addArtifactsTargetsBuildTasks(buildContext, targetsMap, cleanTasksMap, buildTasksMap);
    // TODO send a message if nothing to build
    Set<String> rootPaths = buildTasksMap.keySet();

    AtomicInteger successCounter = new AtomicInteger();
    AtomicInteger errorCounter = new AtomicInteger();

    TaskCallback taskCallback = buildCallback == null ? null : new TaskCallback() {
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
          buildCallback.finished(false, errors, 0, buildContext);
        }
      }
    };

    String gradleVmOptions = GradleSettings.getInstance(buildContext.getProject()).getGradleVmOptions();
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
      ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, buildContext.getProject(), GradleConstants.SYSTEM_ID,
                                 taskCallback, ProgressExecutionMode.IN_BACKGROUND_ASYNC, false);
    }
  }

  @Override
  public boolean canBuild(@NotNull BuildTarget buildTarget) {
    if (!GradleSystemRunningSettings.getInstance().isUseGradleAwareMake()) return false;
    if (buildTarget instanceof ModuleBuildTarget) {
      return ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, ((ModuleBuildTarget)buildTarget).getModule());
    }
    if (buildTarget instanceof ArtifactBuildTarget) {
      Artifact artifact = ((ArtifactBuildTarget)buildTarget).getArtifact();
      for (GradleArtifactBuildTasksProvider buildTasksProvider : GradleArtifactBuildTasksProvider.EP_NAME.getExtensions()) {
        if (buildTasksProvider.isApplicable(artifact)) return true;
      }
    }
    return false;
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile runProfile) {
    if (!GradleSystemRunningSettings.getInstance().isUseGradleAwareMake()) return false;
    if (runProfile instanceof ApplicationConfiguration) {
      JavaRunConfigurationModule module = ((ApplicationConfiguration)runProfile).getConfigurationModule();
      return ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module.getModule());
    }
    return false;
  }

  @Override
  public ExecutionEnvironment createExecutionEnvironment(@NotNull RunProfile runProfile,
                                                         @NotNull Executor executor,
                                                         @NotNull ExecutionTarget executionTarget,
                                                         @NotNull Project project,
                                                         @Nullable RunnerSettings runnerSettings,
                                                         @Nullable ConfigurationPerRunnerSettings configurationSettings,
                                                         @Nullable RunnerAndConfigurationSettings settings) {
    if (runProfile instanceof ApplicationConfiguration) {
      return new GradleApplicationEnvironmentBuilder().build(
        runProfile, executor, executionTarget, project, runnerSettings, configurationSettings, settings);
    }
    return null;
  }

  private static void addModulesTargetsBuildTasks(@NotNull BuildContext buildContext,
                                                  @NotNull Map<Class<? extends BuildTarget>, List<BuildTarget>> targetsMap,
                                                  @NotNull MultiMap<String, String> cleanTasksMap,
                                                  @NotNull MultiMap<String, String> buildTasksMap) {
    Collection<? extends BuildTarget> buildTargets = targetsMap.get(ModuleBuildTarget.class);
    if (!ContainerUtil.isEmpty(buildTargets)) {
      Module[] modules = ContainerUtil.map2Array(buildTargets, Module.class, target -> ModuleBuildTarget.class.cast(target).getModule());
      addModulesTargetsBuildTasks(buildContext, modules, cleanTasksMap, buildTasksMap);
    }
  }

  private static void addFilesTargetsBuildTasks(BuildContext buildContext,
                                                Map<Class<? extends BuildTarget>, List<BuildTarget>> targetsMap,
                                                MultiMap<String, String> cleanTasksMap, MultiMap<String, String> buildTasksMap) {
    Collection<? extends BuildTarget> buildTargets = targetsMap.get(ModuleFilesBuildTarget.class);
    if (!ContainerUtil.isEmpty(buildTargets)) {
      // TODO there should be 'gradle' way to build files instead of the whole related modules
      Module[] modules =
        ContainerUtil.map2Array(buildTargets, Module.class, target -> ModuleFilesBuildTarget.class.cast(target).getModule());
      addModulesTargetsBuildTasks(buildContext, modules, cleanTasksMap, buildTasksMap);
    }
  }

  private static void addModulesTargetsBuildTasks(@NotNull BuildContext buildContext,
                                                  @NotNull Module[] modules,
                                                  @NotNull MultiMap<String, String> cleanTasksMap,
                                                  @NotNull MultiMap<String, String> buildTasksMap) {

    final CachedModuleDataFinder moduleDataFinder = new CachedModuleDataFinder();
    for (Module module : modules) {
      final String rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
      if (rootProjectPath == null) continue;

      final String projectId = ExternalSystemApiUtil.getExternalProjectId(module);
      if (projectId == null) continue;
      final String externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
      if (externalProjectPath == null || StringUtil.endsWith(externalProjectPath, "buildSrc")) continue;

      ExternalProjectInfo projectData =
        ProjectDataManager.getInstance().getExternalProjectData(module.getProject(), GradleConstants.SYSTEM_ID, rootProjectPath);
      if (projectData == null) continue;

      DataNode<ProjectData> projectStructure = projectData.getExternalProjectStructure();
      if (projectStructure == null) continue;

      final DataNode<ModuleData> moduleDataNode = moduleDataFinder.findModuleData(projectStructure, externalProjectPath);
      if (moduleDataNode == null) continue;


      List<String> tasks = ContainerUtil.mapNotNull(ExternalSystemApiUtil.findAll(moduleDataNode, ProjectKeys.TASK),
                                                    node -> node.getData().isInherited() ? null : node.getData().getName());

      Collection<String> cleanTasks = cleanTasksMap.getModifiable(rootProjectPath);
      Collection<String> buildTasks = buildTasksMap.getModifiable(rootProjectPath);
      final String moduleType = ExternalSystemApiUtil.getExternalModuleType(module);
      final String gradlePath;

      if (GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY.equals(moduleType)) {
        int lastColonIndex = projectId.lastIndexOf(':');
        assert lastColonIndex != -1;
        int firstColonIndex = projectId.indexOf(':');

        gradlePath = projectId.substring(firstColonIndex, lastColonIndex);
        String sourceSetName = projectId.substring(lastColonIndex + 1);
        String task = "main".equals(sourceSetName) ? "classes" : sourceSetName + "Classes";
        if (tasks.contains(task)) {
          if (!buildContext.isIncrementalBuild()) {
            cleanTasks.add(gradlePath + ":clean" + StringUtil.capitalize(task));
          }
          buildTasks.add(gradlePath + ":" + task);
        }
        else if ("main".equals(sourceSetName) || "test".equals(sourceSetName)) {
          if (!buildContext.isIncrementalBuild()) {
            cleanTasks.add(gradlePath + ":clean");
          }
          buildTasks.add(gradlePath + ":build");
        }
      }
      else {
        GradleProjectSettings projectSettings =
          GradleSettings.getInstance(buildContext.getProject()).getLinkedProjectSettings(rootProjectPath);
        boolean sourceSetAwareMode = projectSettings != null && projectSettings.isResolveModulePerSourceSet();
        if (!sourceSetAwareMode) {
          gradlePath = projectId.charAt(0) == ':' ? projectId : "";
          if (!buildContext.isIncrementalBuild()) {
            if (tasks.contains("classes")) {
              cleanTasks.add((StringUtil.equals(rootProjectPath, externalProjectPath) ? ":cleanClasses" : gradlePath + ":cleanClasses"));
            }
            else {
              cleanTasks.add((StringUtil.equals(rootProjectPath, externalProjectPath) ? "clean" : gradlePath + ":clean"));
            }
          }
          if (tasks.contains("classes")) {
            buildTasks.add((StringUtil.equals(rootProjectPath, externalProjectPath) ? ":classes" : gradlePath + ":classes"));
          }
          else {
            buildTasks.add((StringUtil.equals(rootProjectPath, externalProjectPath) ? "build" : gradlePath + ":build"));
          }
        }
      }
    }
  }

  private static void addArtifactsTargetsBuildTasks(BuildContext buildContext,
                                                    Map<Class<? extends BuildTarget>, List<BuildTarget>> targetsMap,
                                                    MultiMap<String, String> cleanTasksMap, MultiMap<String, String> buildTasksMap) {

    Collection<? extends BuildTarget> buildTargets = targetsMap.get(ArtifactBuildTarget.class);
    if (!ContainerUtil.isEmpty(buildTargets)) {
      Artifact[] artifacts =
        ContainerUtil.map2Array(buildTargets, Artifact.class, target -> ArtifactBuildTarget.class.cast(target).getArtifact());

      for (Artifact artifact : artifacts) {
        for (GradleArtifactBuildTasksProvider buildTasksProvider : GradleArtifactBuildTasksProvider.EP_NAME.getExtensions()) {
          if (buildTasksProvider.isApplicable(artifact)) {
            buildTasksProvider.addArtifactsTargetsBuildTasks(
              buildContext,
              task -> cleanTasksMap.putValue(task.getLinkedExternalProjectPath(), task.getName()),
              task -> buildTasksMap.putValue(task.getLinkedExternalProjectPath(), task.getName()),
              artifact
            );
          }
        }
      }
    }
  }
}
