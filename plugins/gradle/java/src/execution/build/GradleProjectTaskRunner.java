// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build;

import com.intellij.build.BuildViewManager;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.scratch.JavaScratchConfiguration;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.task.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.plugins.gradle.service.execution.GradleInitScriptUtil;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration.PROGRESS_LISTENER_KEY;
import static com.intellij.openapi.util.text.StringUtil.*;

/**
 * @author Vladislav.Soroka
 */
@SuppressWarnings({"GrUnresolvedAccess", "GroovyAssignabilityCheck"}) // suppress warnings for injected Gradle/Groovy code
public final class GradleProjectTaskRunner extends ProjectTaskRunner {

  private static final Logger LOG = Logger.getInstance(GradleProjectTaskRunner.class);

  private static final String BUILD_INIT_SCRIPT_NAME = "ijJvmBuildInit";

  @Override
  public Promise<Result> run(@NotNull Project project,
                             @NotNull ProjectTaskContext context,
                             ProjectTask @NotNull ... tasks) {
    AsyncPromise<Result> resultPromise = new AsyncPromise<>();
    TasksExecutionSettingsBuilder executionSettingsBuilder = new TasksExecutionSettingsBuilder(project, tasks);
    Set<String> rootPaths = executionSettingsBuilder.getRootPaths();
    if (rootPaths.isEmpty()) {
      LOG.warn("Nothing will be run for: " + Arrays.toString(tasks));
      resultPromise.setResult(TaskRunnerResults.SUCCESS);
      return resultPromise;
    }

    AtomicInteger successCounter = new AtomicInteger();
    AtomicInteger errorCounter = new AtomicInteger();

    File outputPathsFile = createTempOutputPathsFileIfNeeded(context);
    TaskCallback taskCallback = new TaskCallback() {
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
            try {
              if (GradleImprovedHotswapDetection.isEnabled()) {
                GradleImprovedHotswapDetection.processInitScriptOutput(context, outputPathsFile);
              }
              else {
                Set<String> affectedRoots = getAffectedOutputRoots(outputPathsFile, context, executionSettingsBuilder);
                if (!affectedRoots.isEmpty()) {
                  if (context.isCollectionOfGeneratedFilesEnabled()) {
                    context.addDirtyOutputPathsProvider(() -> affectedRoots);
                  }
                  // refresh on output roots is required in order for the order enumerator to see all roots via VFS
                  // have to refresh in case of errors too, because run configuration may be set to ignore errors
                  CompilerUtil.refreshOutputRoots(affectedRoots);
                }
              }
            }
            finally {
              if (outputPathsFile != null) {
                FileUtil.delete(outputPathsFile);
              }
            }
          }
          resultPromise.setResult(errors > 0 ? TaskRunnerResults.FAILURE : TaskRunnerResults.SUCCESS);
        }
        else {
          if (successes + errors > rootPaths.size()) {
            LOG.error("Unexpected callback!");
          }
        }
      }
    };

    for (String rootProjectPath : rootPaths) {
      if (!executionSettingsBuilder.containsTasksToExecuteFor(rootProjectPath)) {
        taskCallback.onSuccess();
        LOG.warn("Nothing will be run for: " + Arrays.toString(tasks) + " at '" + rootProjectPath + "'");
        continue;
      }

      if (outputPathsFile != null && context.isCollectionOfGeneratedFilesEnabled()) {
        executionSettingsBuilder.addInitScripts(rootProjectPath, GradleInitScriptUtil.loadHotswapDetectionInitScript(
          GradleImprovedHotswapDetection.isEnabled(),
          FileUtil.toCanonicalPath(outputPathsFile.getAbsolutePath())
        ));
      }

      ExternalSystemTaskExecutionSettings settings = executionSettingsBuilder.build(rootProjectPath);
      UserDataHolderBase userData = new UserDataHolderBase();
      userData.putUserData(PROGRESS_LISTENER_KEY, BuildViewManager.class);
      userData.putUserData(GradleTaskManager.VERSION_SPECIFIC_SCRIPTS_KEY,
                           executionSettingsBuilder.getVersionedInitScripts(rootProjectPath));
      userData.putUserData(GradleTaskManager.INIT_SCRIPT_KEY, executionSettingsBuilder.getInitScript(rootProjectPath));
      userData.putUserData(GradleTaskManager.INIT_SCRIPT_PREFIX_KEY, BUILD_INIT_SCRIPT_NAME);

      ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID,
                                 taskCallback, ProgressExecutionMode.IN_BACKGROUND_ASYNC, false, userData);
    }
    return resultPromise;
  }

  private static @Nullable File createTempOutputPathsFileIfNeeded(@NotNull ProjectTaskContext context) {
    File outputFile = null;
    if (context.isCollectionOfGeneratedFilesEnabled()) {
      try {
        outputFile = FileUtil.createTempFile("output", ".paths", true);
      }
      catch (IOException e) {
        LOG.warn("Can not create temp file to collect Gradle tasks output paths", e);
      }
    }
    return outputFile;
  }

  private static @NotNull Set<String> getAffectedOutputRoots(@Nullable File outputPathsFile,
                                                             @NotNull ProjectTaskContext context,
                                                             @NotNull TasksExecutionSettingsBuilder executionSettingsBuilder) {
    Set<String> affectedRoots = null;
    if (outputPathsFile != null && context.isCollectionOfGeneratedFilesEnabled()) {
      try {
        String content = FileUtil.loadFile(outputPathsFile);
        affectedRoots = isEmpty(content) ? Collections.emptySet() :
                        Arrays.stream(splitByLines(content, true)).collect(Collectors.toSet());
      }
      catch (IOException e) {
        LOG.warn("Can not load temp file with collected Gradle tasks output paths", e);
      }
    }
    if (affectedRoots == null) {
      List<Module> affectedModules = executionSettingsBuilder.getAffectedModules();
      affectedRoots = ContainerUtil.newHashSet(CompilerPaths.getOutputPaths(affectedModules.toArray(Module.EMPTY_ARRAY)));
    }
    return affectedRoots;
  }

  @Override
  public boolean canRun(@NotNull Project project, @NotNull ProjectTask projectTask, @Nullable ProjectTaskContext context) {
    if (context != null && context.getRunConfiguration() instanceof JavaScratchConfiguration) {
      return false;
    }
    return canRun(projectTask);
  }

  @Override
  public boolean canRun(@NotNull ProjectTask projectTask) {
    if (projectTask instanceof ModuleBuildTask moduleBuildTask) {
      return isDelegatedBuildEnabled(moduleBuildTask.getModule());
    }
    if (projectTask instanceof BuildTask buildTask) {
      return GradleBuildTasksProvider.EP_NAME.findFirstSafe(it -> it.isApplicable(buildTask)) != null;
    }
    if (projectTask instanceof ExecuteRunConfigurationTask executeRunConfigurationTask) {
      var runProfile = executeRunConfigurationTask.getRunProfile();
      if (runProfile instanceof ModuleBasedConfiguration<?, ?> moduleRunProfile) {
        var module = moduleRunProfile.getConfigurationModule().getModule();
        if (!isDelegatedBuildEnabled(module)) {
          return false;
        }
      }
      return GradleExecutionEnvironmentProvider.EP_NAME.findFirstSafe(it -> it.isApplicable(executeRunConfigurationTask)) != null;
    }
    return false;
  }

  private static boolean isDelegatedBuildEnabled(@Nullable Module module) {
    var externalProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
    return externalProjectPath != null &&
           ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module) &&
           GradleProjectSettings.isDelegatedBuildEnabled(module.getProject(), externalProjectPath);
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

  @Override
  public @Nullable ExecutionEnvironment createExecutionEnvironment(@NotNull Project project, ProjectTask @NotNull ... tasks) {
    ExecutionEnvironment environment = super.createExecutionEnvironment(project, tasks);
    if (environment != null) {
      return environment;
    }
    TasksExecutionSettingsBuilder executionSettingsBuilder = new TasksExecutionSettingsBuilder(project, tasks);
    Set<String> rootPaths = executionSettingsBuilder.getRootPaths();
    if (rootPaths.size() != 1) {
      return null;
    }
    String rootProjectPath = rootPaths.iterator().next();
    ExternalSystemTaskExecutionSettings settings = executionSettingsBuilder.build(rootProjectPath);

    environment =
      ExternalSystemUtil.createExecutionEnvironment(project, GradleConstants.SYSTEM_ID, settings, DefaultRunExecutor.EXECUTOR_ID);
    if (environment == null) {
      LOG.warn("Execution environment for " + GradleConstants.SYSTEM_ID + " is null");
      return null;
    }

    RunnerAndConfigurationSettings runnerAndConfigurationSettings = environment.getRunnerAndConfigurationSettings();
    assert runnerAndConfigurationSettings != null;
    ExternalSystemRunConfiguration runConfiguration = (ExternalSystemRunConfiguration)runnerAndConfigurationSettings.getConfiguration();
    runConfiguration.putUserData(GradleTaskManager.VERSION_SPECIFIC_SCRIPTS_KEY,
                                 executionSettingsBuilder.getVersionedInitScripts(rootProjectPath));
    runConfiguration.putUserData(GradleTaskManager.INIT_SCRIPT_KEY, executionSettingsBuilder.getInitScript(rootProjectPath));
    runConfiguration.putUserData(GradleTaskManager.INIT_SCRIPT_PREFIX_KEY, BUILD_INIT_SCRIPT_NAME);
    return environment;
  }

  @Override
  public boolean isFileGeneratedEventsSupported() {
    return true;
  }
}
