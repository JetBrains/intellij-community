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

import com.intellij.activity.*;
import com.intellij.activity.impl.InternalActivityRunner;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
public class GradleActivityRunner extends ActivityRunner {
  @Override
  public void run(@NotNull Project project,
                  @NotNull ActivityContext context,
                  @Nullable ActivityChunkStatusNotification callback,
                  @NotNull Collection<? extends Activity> activities) {
    String executionName = "Gradle build";

    MultiMap<String, String> buildTasksMap = MultiMap.createLinkedSet();
    MultiMap<String, String> cleanTasksMap = MultiMap.createLinkedSet();

    Map<Class<? extends Activity>, List<Activity>> activityMap = InternalActivityRunner.groupBy(activities);

    addModulesBuildActivityTasks(activityMap.get(ModuleBuildActivity.class), cleanTasksMap, buildTasksMap);
    // TODO there should be 'gradle' way to build files instead of related modules entirely
    addModulesBuildActivityTasks(activityMap.get(ModuleFilesBuildActivity.class), cleanTasksMap, buildTasksMap);
    addArtifactsBuildActivityTasks(activityMap.get(ArtifactBuildActivity.class), cleanTasksMap, buildTasksMap);

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
          callback.finished(false, errors, 0);
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
  public boolean canRun(@NotNull Activity activity) {
    if (!GradleSystemRunningSettings.getInstance().isUseGradleAwareMake()) return false;
    if (activity instanceof ModuleBuildActivity) {
      return ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, ((ModuleBuildActivity)activity).getModule());
    }
    if (activity instanceof ArtifactBuildActivity) {
      ArtifactBuildActivity artifactBuildActivity = (ArtifactBuildActivity)activity;
      for (GradleArtifactBuildTasksProvider buildTasksProvider : GradleArtifactBuildTasksProvider.EP_NAME.getExtensions()) {
        if (buildTasksProvider.isApplicable(artifactBuildActivity)) return true;
      }
    }

    if (activity instanceof RunActivity) {
      if (!GradleSystemRunningSettings.getInstance().isUseGradleAwareMake()) return false;

      RunProfile runProfile = ((RunActivity)activity).getRunProfile();
      if (runProfile instanceof ApplicationConfiguration) {
        JavaRunConfigurationModule module = ((ApplicationConfiguration)runProfile).getConfigurationModule();
        return ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module.getModule());
      }
    }
    return false;
  }


  @Override
  public ExecutionEnvironment createActivityExecutionEnvironment(@NotNull Project project, @NotNull RunActivity activity) {
    if (activity.getRunProfile() instanceof ApplicationConfiguration) {
      return new GradleApplicationEnvironmentBuilder().build(project, activity);
    }
    return null;
  }

  private static void addModulesBuildActivityTasks(@Nullable Collection<? extends Activity> activities,
                                                   @NotNull MultiMap<String, String> cleanTasksMap,
                                                   @NotNull MultiMap<String, String> buildTasksMap) {
    if (ContainerUtil.isEmpty(activities)) return;

    final CachedModuleDataFinder moduleDataFinder = new CachedModuleDataFinder();
    for (Activity activity : activities) {
      if (!(activity instanceof ModuleBuildActivity)) continue;

      ModuleBuildActivity moduleBuildActivity = (ModuleBuildActivity)activity;
      Module module = moduleBuildActivity.getModule();

      final String rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
      if (rootProjectPath == null) continue;

      final String projectId = ExternalSystemApiUtil.getExternalProjectId(module);
      if (projectId == null) continue;
      final String externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
      if (externalProjectPath == null || StringUtil.endsWith(externalProjectPath, "buildSrc")) continue;

      final DataNode<ModuleData> moduleDataNode = moduleDataFinder.findModuleData(module);
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
          if (!moduleBuildActivity.isIncrementalBuild()) {
            cleanTasks.add(gradlePath + ":clean" + StringUtil.capitalize(task));
          }
          buildTasks.add(gradlePath + ":" + task);
        }
        else if ("main".equals(sourceSetName) || "test".equals(sourceSetName)) {
          if (!moduleBuildActivity.isIncrementalBuild()) {
            cleanTasks.add(gradlePath + ":clean");
          }
          buildTasks.add(gradlePath + ":build");
        }
      }
      else {
        gradlePath = projectId.charAt(0) == ':' ? projectId : "";
        if (!moduleBuildActivity.isIncrementalBuild()) {
          if (tasks.contains("classes")) {
            cleanTasks.add((StringUtil.equals(rootProjectPath, externalProjectPath) ? ":cleanClasses" : gradlePath + ":cleanClasses"));
          }
          else if(tasks.contains("clean")){
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

  private static void addArtifactsBuildActivityTasks(@Nullable Collection<? extends Activity> activities,
                                                     @NotNull MultiMap<String, String> cleanTasksMap,
                                                     @NotNull MultiMap<String, String> buildTasksMap) {
    if (ContainerUtil.isEmpty(activities)) return;

    for (Activity activity : activities) {
      if (!(activity instanceof ArtifactBuildActivity)) continue;

      ArtifactBuildActivity artifactBuildActivity = (ArtifactBuildActivity)activity;
        for (GradleArtifactBuildTasksProvider buildTasksProvider : GradleArtifactBuildTasksProvider.EP_NAME.getExtensions()) {
          if (buildTasksProvider.isApplicable(artifactBuildActivity)) {
            buildTasksProvider.addArtifactsTargetsBuildTasks(
              artifactBuildActivity,
              task -> cleanTasksMap.putValue(task.getLinkedExternalProjectPath(), task.getName()),
              task -> buildTasksMap.putValue(task.getLinkedExternalProjectPath(), task.getName())
            );
          }
        }
      }
  }
}
