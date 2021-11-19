// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.task.*;
import com.intellij.task.impl.JpsProjectTaskRunner;
import com.intellij.util.CommonProcessors;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.MultiMap;
import org.gradle.api.internal.jvm.ClassDirectoryBinaryNamingScheme;
import org.gradle.util.GradleVersion;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.service.task.VersionSpecificInitScript;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleModuleData;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration.PROGRESS_LISTENER_KEY;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.*;
import static com.intellij.openapi.util.text.StringUtil.*;
import static org.jetbrains.plugins.gradle.execution.GradleRunnerUtil.resolveProjectPath;

/**
 * @author Vladislav.Soroka
 */
@SuppressWarnings({"GrUnresolvedAccess", "GroovyAssignabilityCheck"}) // suppress warnings for injected Gradle/Groovy code
public class GradleProjectTaskRunner extends ProjectTaskRunner {
  private static final Logger LOG = Logger.getInstance(GradleProjectTaskRunner.class);

  @Language("Groovy")
  private static final String FORCE_COMPILE_TASKS_INIT_SCRIPT_TEMPLATE = "projectsEvaluated { \n" +
                                                                         "  rootProject.findProject('%s')?.tasks?.withType(AbstractCompile) {  \n" +
                                                                         "    outputs.upToDateWhen { false } \n" +
                                                                         "  } \n" +
                                                                         "}\n";

  @Language("Groovy")
  private static final String COLLECT_OUTPUT_PATHS_USING_SERVICES_INIT_SCRIPT_TEMPLATE = "import org.gradle.tooling.events.OperationCompletionListener\n" +
                                                                                         "import org.gradle.tooling.events.FinishEvent\n" +
                                                                                         "import org.gradle.api.services.BuildService\n" +
                                                                                         "import org.gradle.api.services.BuildServiceParameters\n" +
                                                                                         "import org.gradle.util.GradleVersion\n" +
                                                                                         "import org.gradle.api.Task\n" +
                                                                                         "\n" +
                                                                                         "def outputFile = new File(\"%s\")\n" +
                                                                                         "\n" +
                                                                                         "abstract class OutputPathCollectorService\n" +
                                                                                         "        implements BuildService<OutputPathCollectorService.Params>, AutoCloseable {\n" +
                                                                                         "\n" +
                                                                                         "    interface Params extends BuildServiceParameters {\n" +
                                                                                         "        Property<File> getOutputFile()\n" +
                                                                                         "    }\n" +
                                                                                         "\n" +
                                                                                         "    Set<Task> tasks = new HashSet<Task>()\n" +
                                                                                         "\n" +
                                                                                         "    void registerTask(Task t) {\n" +
                                                                                         "        tasks.add(t)\n" +
                                                                                         "    }\n" +
                                                                                         "\n" +
                                                                                         "    @Override\n" +
                                                                                         "    void close() throws Exception {\n" +
                                                                                         "        def outputFile = getParameters().outputFile.get()\n" +
                                                                                         "        tasks.each { Task task ->\n" +
                                                                                         "            def state = task.state\n" +
                                                                                         "            def work = state.didWork\n" +
                                                                                         "            def fromCache = state.skipped && state.skipMessage == 'FROM-CACHE'\n" +
                                                                                         "            def hasOutput = task.outputs.hasOutput\n" +
                                                                                         "            if ((work || fromCache) && hasOutput) {\n" +
                                                                                         "                task.outputs.files.files.each { outputFile.append(it.path + '\\n') }\n" +
                                                                                         "            }\n" +
                                                                                         "        }\n" +
                                                                                         "    }\n" +
                                                                                         "}\n" +
                                                                                         "\n" +
                                                                                         "Provider<OutputPathCollectorService> provider = gradle.sharedServices.registerIfAbsent(\"outputPathCollectorService\",\n" +
                                                                                         "        OutputPathCollectorService) { it.parameters.outputFile.set(outputFile)  }\n" +
                                                                                         "\n" +
                                                                                         "gradle.taskGraph.whenReady { TaskExecutionGraph tg ->\n" +
                                                                                         "    tg.allTasks.each { Task t ->\n" +
                                                                                         "        t.onlyIf {\n" +
                                                                                         "            provider.get().registerTask(t)\n" +
                                                                                         "            return true\n" +
                                                                                         "        }\n" +
                                                                                         "    }\n" +
                                                                                         "}\n";

  @Language("Groovy")
  private static final String COLLECT_OUTPUT_PATHS_INIT_SCRIPT_TEMPLATE = "def outputFile = new File(\"%s\")\n" +
                                                                          "def effectiveTasks = []\n" +
                                                                          "gradle.taskGraph.addTaskExecutionListener(new TaskExecutionAdapter() {\n" +
                                                                          "    void afterExecute(Task task, TaskState state) {\n" +
                                                                          "        if ((state.didWork || (state.skipped && state.skipMessage == 'FROM-CACHE')) && task.outputs.hasOutput) {\n" +
                                                                          "            effectiveTasks.add(task)\n" +
                                                                          "        }\n" +
                                                                          "    }\n" +
                                                                          "})\n" +
                                                                          "gradle.addBuildListener(new BuildAdapter() {\n" +
                                                                          "    void buildFinished(BuildResult result) {\n" +
                                                                          "        effectiveTasks.each { Task task ->\n" +
                                                                          "            task.outputs.files.files.each { outputFile.append(it.path + '\\n') }\n" +
                                                                          "        }\n" +
                                                                          "    }\n" +
                                                                          "})\n";

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
    if (projectTask instanceof ProjectModelBuildTask) {
      ProjectModelBuildTask<?> buildTask = (ProjectModelBuildTask<?>)projectTask;
      if (buildTask.getBuildableElement() instanceof Artifact) {
        for (GradleBuildTasksProvider buildTasksProvider : GradleBuildTasksProvider.EP_NAME.getExtensions()) {
          if (buildTasksProvider.isApplicable(buildTask)) return true;
        }
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

  private static List<Module> addModulesBuildTasks(@Nullable Collection<? extends ProjectTask> projectTasks,
                                                   @NotNull MultiMap<String, String> buildTasksMap,
                                                   @NotNull MultiMap<String, String> initScripts) {
    if (ContainerUtil.isEmpty(projectTasks)) return Collections.emptyList();

    List<Module> affectedModules = new SmartList<>();
    Map<Module, String> rootPathsMap = FactoryMap.create(module -> notNullize(resolveProjectPath(module)));
    for (ProjectTask projectTask : projectTasks) {
      if (!(projectTask instanceof ModuleBuildTask)) continue;

      ModuleBuildTask moduleBuildTask = (ModuleBuildTask)projectTask;
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

      boolean isGradleProjectDirUsedToRunTasks = gradleModuleData.getDirectoryToRunTask().equals(gradleModuleData.getGradleProjectDir());
      if (!isGradleProjectDirUsedToRunTasks) {
        rootProjectPath = gradleModuleData.getDirectoryToRunTask();
      }

      // all buildSrc runtime projects will be built by gradle implicitly
      if (gradleModuleData.isBuildSrcModule()) {
        continue;
      }

      String gradlePath = gradleModuleData.getGradlePath();
      List<TaskData> taskDataList =
        ContainerUtil.mapNotNull(gradleModuleData.findAll(ProjectKeys.TASK), taskData -> taskData.isInherited() ? null : taskData);
      if (projectTasks.isEmpty()) continue;

      String taskPathPrefix = trimEnd(gradleModuleData.getFullGradlePath(), ":") + ":";
      List<String> gradleModuleTasks = ContainerUtil.map(taskDataList, data -> trimStart(data.getName(), taskPathPrefix));

      Collection<String> projectInitScripts = initScripts.getModifiable(rootProjectPath);
      Collection<String> buildRootTasks = buildTasksMap.getModifiable(rootProjectPath);
      final String moduleType = getExternalModuleType(module);

      if (!moduleBuildTask.isIncrementalBuild() && !(moduleBuildTask instanceof ModuleFilesBuildTask)) {
        projectInitScripts.add(String.format(FORCE_COMPILE_TASKS_INIT_SCRIPT_TEMPLATE, gradlePath));
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

  @NotNull
  private static String getTaskName(@NotNull String taskPrefix, @NotNull String taskSuffix, @Nullable String sourceSetName) {
    if (Strings.isEmpty(sourceSetName)) sourceSetName = "main";
    return new ClassDirectoryBinaryNamingScheme(sourceSetName).getTaskName(taskPrefix, taskSuffix);
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

  private static void addArtifactsBuildTasks(@Nullable Collection<? extends ProjectTask> tasks,
                                             @NotNull MultiMap<String, String> cleanTasksMap,
                                             @NotNull MultiMap<String, String> buildTasksMap,
                                             @NotNull MultiMap<String, VersionSpecificInitScript> versionedInitScripts) {
    if (ContainerUtil.isEmpty(tasks)) return;

    for (ProjectTask projectTask : tasks) {
      if (!(projectTask instanceof ProjectModelBuildTask)) continue;

      ProjectModelBuildTask<?> projectModelBuildTask = (ProjectModelBuildTask<?>)projectTask;
      for (GradleBuildTasksProvider buildTasksProvider : GradleBuildTasksProvider.EP_NAME.getExtensions()) {
        if (buildTasksProvider.isApplicable(projectModelBuildTask)) {
          buildTasksProvider.addBuildTasks(
            projectModelBuildTask,
            task -> cleanTasksMap.putValue(task.getLinkedExternalProjectPath(), task.getName()),
            task -> buildTasksMap.putValue(task.getLinkedExternalProjectPath(), task.getName()),
            (String path, VersionSpecificInitScript script) -> versionedInitScripts.putValue(path, script)
          );
        }
      }
    }
  }

  private static class TasksExecutionSettingsBuilder {
    private final MultiMap<String, String> buildTasksMap = MultiMap.createLinkedSet();
    private final MultiMap<String, String> cleanTasksMap = MultiMap.createLinkedSet();
    private final MultiMap<String, String> initScripts = MultiMap.createLinkedSet();
    private final MultiMap<String, VersionSpecificInitScript> versionedInitScripts = MultiMap.createLinkedSet();

    private final List<Module> modulesToBuild;
    private final List<Module> modulesOfResourcesToBuild;
    private final List<Module> modulesOfFiles;

    private final String gradleVmOptions;

    private TasksExecutionSettingsBuilder(@NotNull Project project, ProjectTask @NotNull ... tasks) {
      gradleVmOptions = GradleSettings.getInstance(project).getGradleVmOptions();
      Map<Class<? extends ProjectTask>, List<ProjectTask>> taskMap = JpsProjectTaskRunner.groupBy(Arrays.asList(tasks));
      modulesToBuild = addModulesBuildTasks(taskMap.get(ModuleBuildTask.class), buildTasksMap, initScripts);
      modulesOfResourcesToBuild = addModulesBuildTasks(taskMap.get(ModuleResourcesBuildTask.class), buildTasksMap, initScripts);
      modulesOfFiles = addModulesBuildTasks(taskMap.get(ModuleFilesBuildTask.class), buildTasksMap, initScripts);
      addArtifactsBuildTasks(taskMap.get(ProjectModelBuildTask.class), cleanTasksMap, buildTasksMap, versionedInitScripts);
    }

    private Iterable<String> getTasksToExecute(String rootProjectPath) {
      Collection<String> buildTasks = buildTasksMap.get(rootProjectPath);
      Collection<String> cleanTasks = cleanTasksMap.get(rootProjectPath);
      return ContainerUtil.concat(cleanTasks, buildTasks);
    }

    Set<String> getRootPaths() {
      return buildTasksMap.keySet();
    }

    List<Module> getAffectedModules() {
      return ContainerUtil.concat(modulesToBuild, modulesOfResourcesToBuild, modulesOfFiles);
    }

    public void addInitScripts(String rootProjectPath, VersionSpecificInitScript... initScript) {
      Collection<VersionSpecificInitScript> versionSpecificInitScripts = versionedInitScripts.getModifiable(rootProjectPath);
      Collections.addAll(versionSpecificInitScripts, initScript);
    }

    public Collection<VersionSpecificInitScript> getVersionedInitScripts(String rootProjectPath) {
      return versionedInitScripts.get(rootProjectPath);
    }

    public Collection<String> getInitScripts(String rootProjectPath) {
      return initScripts.get(rootProjectPath);
    }

    public boolean containsTasksToExecuteFor(String rootProjectPath) {
      return getTasksToExecute(rootProjectPath).iterator().hasNext();
    }

    public ExternalSystemTaskExecutionSettings build(String rootProjectPath) {
      ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
      File projectFile = new File(rootProjectPath);
      final String projectName;
      if (projectFile.isFile()) {
        projectName = projectFile.getParentFile().getName();
      }
      else {
        projectName = projectFile.getName();
      }
      String executionName = GradleBundle.message("gradle.execution.name.build.project.", projectName);
      settings.setExecutionName(executionName);
      settings.setExternalProjectPath(rootProjectPath);
      List<@NlsSafe String> taskNames = ContainerUtil.collect(getTasksToExecute(rootProjectPath).iterator());
      settings.setTaskNames(taskNames);
      settings.setVmOptions(gradleVmOptions);
      settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.getId());
      return settings;
    }
  }
}
