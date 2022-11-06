// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build;

import com.intellij.build.BuildViewManager;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.task.*;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.util.GradleVersion;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.service.task.VersionSpecificInitScript;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration.PROGRESS_LISTENER_KEY;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.*;
import static com.intellij.openapi.util.text.StringUtil.*;

/**
 * @author Vladislav.Soroka
 */
@SuppressWarnings({"GrUnresolvedAccess", "GroovyAssignabilityCheck"}) // suppress warnings for injected Gradle/Groovy code
public class GradleProjectTaskRunner extends ProjectTaskRunner {
  private static final Logger LOG = Logger.getInstance(GradleProjectTaskRunner.class);

  @Language("Groovy")
  private static final String COLLECT_OUTPUT_PATHS_USING_SERVICES_INIT_SCRIPT_TEMPLATE = """
    import org.gradle.tooling.events.OperationCompletionListener
    import org.gradle.tooling.events.FinishEvent
    import org.gradle.api.services.BuildService
    import org.gradle.api.services.BuildServiceParameters
    import org.gradle.util.GradleVersion
    import org.gradle.api.Task

    def outputFile = new File("%s")

    abstract class OutputPathCollectorService
            implements BuildService<OutputPathCollectorService.Params>, AutoCloseable {

        interface Params extends BuildServiceParameters {
            Property<File> getOutputFile()
        }

        Set<Task> tasks = new HashSet<Task>()

        void registerTask(Task t) {
            tasks.add(t)
        }

        @Override
        void close() throws Exception {
            def outputFile = getParameters().outputFile.get()
            tasks.each { Task task ->
                def state = task.state
                def work = state.didWork
                def fromCache = state.skipped && state.skipMessage == 'FROM-CACHE'
                def hasOutput = task.outputs.hasOutput
                if ((work || fromCache) && hasOutput) {
                    task.outputs.files.files.each { outputFile.append(it.path + '\\n') }
                }
            }
        }
    }

    Provider<OutputPathCollectorService> provider = gradle.sharedServices.registerIfAbsent("outputPathCollectorService",
            OutputPathCollectorService) { it.parameters.outputFile.set(outputFile)  }

    gradle.taskGraph.whenReady { TaskExecutionGraph tg ->
        tg.allTasks.each { Task t ->
            t.onlyIf {
                provider.get().registerTask(t)
                return true
            }
        }
    }
    """;

  @Language("Groovy")
  private static final String COLLECT_OUTPUT_PATHS_INIT_SCRIPT_TEMPLATE = """
    def outputFile = new File("%s")
    def effectiveTasks = []
    gradle.taskGraph.addTaskExecutionListener(new TaskExecutionAdapter() {
        void afterExecute(Task task, TaskState state) {
            if ((state.didWork || (state.skipped && state.skipMessage == 'FROM-CACHE')) && task.outputs.hasOutput) {
                effectiveTasks.add(task)
            }
        }
    })
    gradle.addBuildListener(new BuildAdapter() {
        void buildFinished(BuildResult result) {
            effectiveTasks.each { Task task ->
                task.outputs.files.files.each { outputFile.append(it.path + '\\n') }
            }
        }
    })
    """;

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
        String outputFilePath = FileUtil.toCanonicalPath(outputPathsFile.getAbsolutePath());
        GradleVersion v68 = GradleVersion.version("6.8");

        String initScript;
        String initScriptUsingService;

        if (GradleImprovedHotswapDetection.isEnabled()) {
          initScript = GradleImprovedHotswapDetection.getInitScript(outputPathsFile);
          initScriptUsingService = GradleImprovedHotswapDetection.getInitScriptUsingService(outputPathsFile);
        }
        else {
          initScript = String.format(COLLECT_OUTPUT_PATHS_INIT_SCRIPT_TEMPLATE, outputFilePath);
          initScriptUsingService = String.format(COLLECT_OUTPUT_PATHS_USING_SERVICES_INIT_SCRIPT_TEMPLATE, outputFilePath);
        }

        var simple = new VersionSpecificInitScript(initScript, "ijpathcollect", v -> v.compareTo(v68) < 0);
        var services = new VersionSpecificInitScript(initScriptUsingService, "ijpathcollect", v -> v.compareTo(v68) >= 0);
        executionSettingsBuilder.addInitScripts(rootProjectPath, simple, services);
      }

      ExternalSystemTaskExecutionSettings settings = executionSettingsBuilder.build(rootProjectPath);
      UserDataHolderBase userData = new UserDataHolderBase();
      userData.putUserData(PROGRESS_LISTENER_KEY, BuildViewManager.class);
      userData.putUserData(GradleTaskManager.VERSION_SPECIFIC_SCRIPTS_KEY,
                           executionSettingsBuilder.getVersionedInitScripts(rootProjectPath));
      userData.putUserData(GradleTaskManager.INIT_SCRIPT_KEY, join(executionSettingsBuilder.getInitScripts(rootProjectPath), System.lineSeparator()));
      userData.putUserData(GradleTaskManager.INIT_SCRIPT_PREFIX_KEY, settings.getExecutionName());

      ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID,
                                 taskCallback, ProgressExecutionMode.IN_BACKGROUND_ASYNC, false, userData);
    }
    return resultPromise;
  }

  @Nullable
  private static File createTempOutputPathsFileIfNeeded(@NotNull ProjectTaskContext context) {
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

  @NotNull
  private static Set<String> getAffectedOutputRoots(@Nullable File outputPathsFile,
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
  public boolean canRun(@NotNull ProjectTask projectTask) {
    if (projectTask instanceof ModuleBuildTask) {
      Module module = ((ModuleBuildTask)projectTask).getModule();
      if (!GradleProjectSettings.isDelegatedBuildEnabled(module)) return false;
      return isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module);
    }
    if (projectTask instanceof BuildTask) {
      BuildTask buildTask = (BuildTask)projectTask;
      for (GradleBuildTasksProvider buildTasksProvider : GradleBuildTasksProvider.EP_NAME.getExtensions()) {
        if (buildTasksProvider.isApplicable(buildTask)) return true;
      }
    }

    if (projectTask instanceof ExecuteRunConfigurationTask) {
      RunProfile runProfile = ((ExecuteRunConfigurationTask)projectTask).getRunProfile();
      if (runProfile instanceof ModuleBasedConfiguration) {
        RunConfigurationModule module = ((ModuleBasedConfiguration<?, ?>)runProfile).getConfigurationModule();
        if (!isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module.getModule()) ||
            !GradleProjectSettings.isDelegatedBuildEnabled(module.getModule())) {
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
    String initScript = join(executionSettingsBuilder.getInitScripts(rootProjectPath), System.lineSeparator());
    runConfiguration.putUserData(GradleTaskManager.INIT_SCRIPT_KEY, initScript);
    runConfiguration.putUserData(GradleTaskManager.INIT_SCRIPT_PREFIX_KEY, settings.getExecutionName());
    return environment;
  }

  @Override
  public boolean isFileGeneratedEventsSupported() {
    return true;
  }
}
