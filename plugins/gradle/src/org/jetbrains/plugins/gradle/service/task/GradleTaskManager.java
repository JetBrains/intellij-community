// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.task;

import com.google.gson.GsonBuilder;
import com.intellij.build.SyncViewManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.target.TargetProgressIndicator;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.gradle.toolingExtension.GradleToolingExtensionClass;
import com.intellij.gradle.toolingExtension.impl.GradleToolingExtensionImplClass;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.externalSystem.rt.ExternalSystemRtClass;
import com.intellij.task.RunConfigurationTaskState;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.api.Task;
import org.gradle.tooling.*;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.data.CompositeBuildData;
import org.jetbrains.plugins.gradle.service.GradleFileModificationTracker;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.service.execution.*;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.project.GradleTasksIndices;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleBuildParticipant;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper.DISPATCH_ADDR_SYS_PROP;
import static com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper.DISPATCH_PORT_SYS_PROP;
import static com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunnableState.*;
import static org.jetbrains.plugins.gradle.util.GradleUtil.determineRootProject;

public class GradleTaskManager implements ExternalSystemTaskManager<GradleExecutionSettings> {

  public static final Key<String> INIT_SCRIPT_KEY = Key.create("INIT_SCRIPT_KEY");
  public static final Key<String> INIT_SCRIPT_PREFIX_KEY = Key.create("INIT_SCRIPT_PREFIX_KEY");
  public static final Key<Collection<VersionSpecificInitScript>> VERSION_SPECIFIC_SCRIPTS_KEY = Key.create("VERSION_SPECIFIC_SCRIPTS_KEY");
  private static final Logger LOG = Logger.getInstance(GradleTaskManager.class);
  private final GradleExecutionHelper myHelper = new GradleExecutionHelper();

  private final Map<ExternalSystemTaskId, CancellationTokenSource> myCancellationMap = new ConcurrentHashMap<>();

  public GradleTaskManager() {
  }

  /**
   * @deprecated use {@link GradleTaskManager#executeTasks(String, ExternalSystemTaskId, GradleExecutionSettings, ExternalSystemTaskNotificationListener)} instead
   */
  @Deprecated
  @Override
  public void executeTasks(
    @NotNull ExternalSystemTaskId id,
    @NotNull List<String> taskNames,
    @NotNull String projectPath,
    @Nullable GradleExecutionSettings settings,
    @Nullable String jvmParametersSetup,
    @NotNull ExternalSystemTaskNotificationListener listener
  ) throws ExternalSystemException {
    ExternalSystemTaskManager.super.executeTasks(id, taskNames, projectPath, settings, jvmParametersSetup, listener);
  }

  @Override
  public void executeTasks(
    @NotNull String projectPath,
    @NotNull ExternalSystemTaskId id,
    @NotNull GradleExecutionSettings settings,
    @NotNull ExternalSystemTaskNotificationListener listener
  ) throws ExternalSystemException {

    if (ExternalSystemApiUtil.isInProcessMode(GradleConstants.SYSTEM_ID)) {
      for (GradleTaskManagerExtension gradleTaskManagerExtension : GradleTaskManagerExtension.EP_NAME.getExtensionList()) {
        if (gradleTaskManagerExtension.executeTasks(projectPath, id, settings, listener)) {
          return;
        }
      }
    }

    CancellationTokenSource cancellationTokenSource = GradleConnector.newCancellationTokenSource();
    CancellationToken cancellationToken = cancellationTokenSource.token();
    myCancellationMap.put(id, cancellationTokenSource);
    try {
      if (settings.getDistributionType() == DistributionType.WRAPPED) {
        String rootProjectPath = determineRootProject(projectPath);
        GradleWrapperHelper.ensureInstalledWrapper(id, rootProjectPath, settings, listener, cancellationToken);
      }
      myHelper.execute(projectPath, settings, id, listener, cancellationToken, connection -> {
        executeTasks(projectPath, id, settings, listener, connection, cancellationToken);
        return null;
      });
    }
    finally {
      myCancellationMap.remove(id);
    }
  }

  private static void executeTasks(
    @NotNull String projectPath,
    @NotNull ExternalSystemTaskId id,
    @NotNull GradleExecutionSettings settings,
    @NotNull ExternalSystemTaskNotificationListener listener,
    @NotNull ProjectConnection connection,
    @NotNull CancellationToken cancellationToken
  ) {
    BuildEnvironment buildEnvironment = null;
    try {
      buildEnvironment = GradleExecutionHelper.getBuildEnvironment(connection, id, listener, cancellationToken, settings);
      var gradleVersion = getGradleVersion(buildEnvironment);

      setupGradleScriptDebugging(settings);
      setupDebuggerDispatchPort(settings);
      setupBuiltInTestEvents(settings, gradleVersion);

      configureTasks(projectPath, id, settings, gradleVersion);

      for (GradleBuildParticipant buildParticipant : settings.getExecutionWorkspace().getBuildParticipants()) {
        settings.withArguments(GradleConstants.INCLUDE_BUILD_CMD_OPTION, buildParticipant.getProjectPath());
      }
      prepareTaskState(id, settings, listener);

      if (Registry.is("gradle.report.recently.saved.paths")) {
        ApplicationManager.getApplication()
          .getService(GradleFileModificationTracker.class)
          .notifyConnectionAboutChangedPaths(connection);
      }

      var operation = isApplicableTestLauncher(id, projectPath, settings, gradleVersion)
                      ? connection.newTestLauncher()
                      : connection.newBuild();
      GradleExecutionHelper.prepareForExecution(connection, operation, cancellationToken, id, settings, listener);
      if (operation instanceof BuildLauncher) {
        ((BuildLauncher)operation).run();
      }
      else {
        ((TestLauncher)operation).run();
      }
    }
    catch (RuntimeException e) {
      LOG.debug("Gradle build launcher error", e);
      final GradleProjectResolverExtension projectResolverChain = GradleProjectResolver.createProjectResolverChain();
      throw projectResolverChain.getUserFriendlyError(buildEnvironment, e, projectPath, null);
    }
  }

  private static @Nullable GradleVersion getGradleVersion(@Nullable BuildEnvironment buildEnvironment) {
    return Optional.ofNullable(buildEnvironment)
      .map(it -> it.getGradle())
      .map(it -> it.getGradleVersion())
      .map(it -> GradleInstallationManager.getGradleVersionSafe(it))
      .orElse(null);
  }

  private static boolean isApplicableTestLauncher(
    @NotNull ExternalSystemTaskId id,
    @NotNull String projectPath,
    @NotNull GradleExecutionSettings settings,
    @Nullable GradleVersion gradleVersion
  ) {
    if (!Registry.is("gradle.testLauncherAPI.enabled")) {
      LOG.debug("TestLauncher isn't applicable: disabled by registry");
      return false;
    }
    if (ExternalSystemExecutionAware.hasTargetEnvironmentConfiguration(settings)) {
      LOG.debug("TestLauncher isn't applicable: unsupported execution with remote target");
      return false;
    }
    if (!settings.isTestTaskRerun()) {
      LOG.debug("TestLauncher isn't applicable: RC doesn't expect task rerun");
      return false;
    }
    if (gradleVersion == null) {
      LOG.debug("TestLauncher isn't applicable: Gradle version cannot be determined");
      return false;
    }
    if (GradleVersionUtil.isGradleOlderThan(gradleVersion, "8.3")) {
      LOG.debug("TestLauncher isn't applicable: unsupported Gradle version: " + gradleVersion);
      return false;
    }
    var project = id.findProject();
    if (project == null) {
      LOG.debug("TestLauncher isn't applicable: Project is already closed");
      return false;
    }
    if (GradleVersionUtil.isGradleOlderThan(gradleVersion, "8.4") && hasProjectIncludedBuild(project, projectPath)) {
      LOG.debug("TestLauncher isn't applicable: Project has included build. " + gradleVersion);
      return false;
    }
    var commandLine = settings.getCommandLine();
    if (!hasJvmTestTasks(commandLine, project, projectPath)) {
      LOG.debug("TestLauncher isn't applicable: RC hasn't JVM test tasks");
      return false;
    }
    if (hasNonJvmTestTasks(commandLine, project, projectPath)) {
      LOG.debug("TestLauncher isn't applicable: RC has non-JVM test tasks");
      return false;
    }
    if (hasNonTestOptions(commandLine)) {
      LOG.debug("TestLauncher isn't applicable: RC tasks have non-test options");
      return false;
    }
    if (hasUnrecognizedOptions(commandLine)) {
      LOG.debug("TestLauncher isn't applicable: RC has unrecognized options");
      return false;
    }
    LOG.debug("TestLauncher is applicable");
    return true;
  }

  private static boolean hasJvmTestTasks(@NotNull GradleCommandLine commandLine, @NotNull Project project, @NotNull String projectPath) {
    var indices = GradleTasksIndices.getInstance(project);
    for (var task : commandLine.getTasks()) {
      var taskData = indices.findTasks(projectPath, task.getName());
      if (ContainerUtil.exists(taskData, it -> it.isJvmTest())) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasNonJvmTestTasks(@NotNull GradleCommandLine commandLine, @NotNull Project project, @NotNull String projectPath) {
    var indices = GradleTasksIndices.getInstance(project);
    for (var task : commandLine.getTasks()) {
      var taskData = indices.findTasks(projectPath, task.getName());
      if (ContainerUtil.exists(taskData, it -> it.isTest() && !it.isJvmTest())) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasProjectIncludedBuild(@NotNull Project project, @NotNull String projectPath) {
    var projectNode = ExternalSystemApiUtil.findProjectNode(project, GradleConstants.SYSTEM_ID, projectPath);
    if (projectNode == null) return false;
    var compositeBuildNode = ExternalSystemApiUtil.find(projectNode, CompositeBuildData.KEY);
    if (compositeBuildNode == null) return false;
    var compositeBuildParticipants = compositeBuildNode.getData().getCompositeParticipants();
    return !compositeBuildParticipants.isEmpty();
  }

  private static boolean hasNonTestOptions(@NotNull GradleCommandLine commandLine) {
    for (var task : commandLine.getTasks()) {
      if (ContainerUtil.exists(task.getOptions(), it -> !GradleCommandLineUtil.isTestPattern(it))) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasUnrecognizedOptions(@NotNull GradleCommandLine commandLine) {
    for (var task : commandLine.getTasks()) {
      if (task.getName().startsWith("-")) {
        return true;
      }
    }
    return false;
  }

  private static void prepareTaskState(@NotNull ExternalSystemTaskId id,
                                       @NotNull GradleExecutionSettings settings,
                                       @NotNull ExternalSystemTaskNotificationListener listener) {
    if (ExternalSystemExecutionAware.hasTargetEnvironmentConfiguration(settings)) return; // Prepared by TargetBuildLauncher.

    RunConfigurationTaskState taskState = settings.getUserData(RunConfigurationTaskState.getKEY());
    if (taskState == null) return;

    LocalTargetEnvironmentRequest request = new LocalTargetEnvironmentRequest();
    TargetProgressIndicator progressIndicator = TargetProgressIndicator.EMPTY;
    try {
      taskState.prepareTargetEnvironmentRequest(request, progressIndicator);
      LocalTargetEnvironment environment = request.prepareEnvironment(progressIndicator);
      String taskStateInitScript = taskState.handleCreatedTargetEnvironment(environment, progressIndicator);
      if (taskStateInitScript != null) {
        var initScriptPath = GradleInitScriptUtil.createInitScript("ijtgttaskstate", taskStateInitScript);
        settings.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, initScriptPath.toString());
      }
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
    listener.onEnvironmentPrepared(id);
  }

  @Override
  public boolean cancelTask(@NotNull ExternalSystemTaskId id, @NotNull ExternalSystemTaskNotificationListener listener)
    throws ExternalSystemException {
    final CancellationTokenSource cancellationTokenSource = myCancellationMap.get(id);
    if (cancellationTokenSource != null) {
      cancellationTokenSource.cancel();
      return true;
    }
    // extension points are available only in IDE process
    if (ExternalSystemApiUtil.isInProcessMode(GradleConstants.SYSTEM_ID)) {
      for (GradleTaskManagerExtension gradleTaskManagerExtension : GradleTaskManagerExtension.EP_NAME.getExtensions()) {
        if (gradleTaskManagerExtension.cancelTask(id, listener)) return true;
      }
    }
    return false;
  }

  /**
   * @deprecated Use {@link ExternalSystemUtil#runTask} instead
   */
  @Deprecated
  public static void appendInitScriptArgument(
    @NotNull List<String> taskNames,
    @Nullable String jvmParametersSetup,
    @NotNull GradleExecutionSettings settings
  ) {
    var id = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, "");
    settings.setTasks(taskNames);
    settings.setJvmParameters(jvmParametersSetup);
    configureTasks("", id, settings, null);
  }

  @ApiStatus.Internal
  public static void configureTasks(
    @NotNull String projectPath,
    @NotNull ExternalSystemTaskId id,
    @NotNull GradleExecutionSettings settings,
    @Nullable GradleVersion gradleVersion
  ) {
    GradleTaskManagerExtension.EP_NAME.forEachExtensionSafe(it -> {
      it.configureTasks(projectPath, id, settings, gradleVersion);
    });

    final String initScript = settings.getUserData(INIT_SCRIPT_KEY);
    if (StringUtil.isNotEmpty(initScript)) {
      var initScriptPrefix = StringUtil.notNullize(settings.getUserData(INIT_SCRIPT_PREFIX_KEY), "ijmiscinit");
      var initScriptPath = GradleInitScriptUtil.createInitScript(initScriptPrefix, initScript);
      settings.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, initScriptPath.toString());
    }

    final Collection<VersionSpecificInitScript> scripts = settings.getUserData(VERSION_SPECIFIC_SCRIPTS_KEY);
    if (gradleVersion != null && scripts != null) {
      for (var script : scripts) {
        if (script.isApplicableTo(gradleVersion) && StringUtil.isNotEmpty(script.getScript())) {
          var initScriptPrefix = StringUtil.notNullize(script.getFilePrefix(), "ijverspecinit");
          var initScriptPath = GradleInitScriptUtil.createInitScript(initScriptPrefix, script.getScript());
          settings.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, initScriptPath.toString());
        }
      }
    }

    if (settings.getArguments().contains(GradleConstants.INIT_SCRIPT_CMD_OPTION)) {
      GradleInitScriptUtil.attachTargetPathMapperInitScript(settings);
    }
  }

  public static void setupGradleScriptDebugging(@NotNull GradleExecutionSettings effectiveSettings) {
    Integer gradleScriptDebugPort = effectiveSettings.getUserData(BUILD_PROCESS_DEBUGGER_PORT_KEY);
    if (effectiveSettings.isDebugServerProcess() && gradleScriptDebugPort != null && gradleScriptDebugPort > 0) {
      String debugAddress;
      String dispatchAddr = effectiveSettings.getUserData(DEBUGGER_DISPATCH_ADDR_KEY);
      if (dispatchAddr != null) {
        debugAddress = dispatchAddr + ":" + gradleScriptDebugPort;
      }
      else {
        boolean isJdk9orLater = ExternalSystemJdkUtil.isJdk9orLater(effectiveSettings.getJavaHome());
        debugAddress = (isJdk9orLater ? "127.0.0.1:" : "") + gradleScriptDebugPort;
      }
      String jvmOpt = ForkedDebuggerHelper.JVM_DEBUG_SETUP_PREFIX + debugAddress;
      effectiveSettings.withVmOption(jvmOpt);
    }
    if (effectiveSettings.isDebugAllEnabled()) {
      effectiveSettings.withArgument("-Didea.gradle.debug.all=true");
    }
  }

  public static void setupDebuggerDispatchPort(@NotNull GradleExecutionSettings effectiveSettings) {
    Integer dispatchPort = effectiveSettings.getUserData(DEBUGGER_DISPATCH_PORT_KEY);
    if (dispatchPort != null) {
      effectiveSettings.withArgument(String.format("-D%s=%d", DISPATCH_PORT_SYS_PROP, dispatchPort));
    }
    String dispatchAddr = effectiveSettings.getUserData(DEBUGGER_DISPATCH_ADDR_KEY);
    if (dispatchAddr != null) {
      effectiveSettings.withArgument(String.format("-D%s=%s", DISPATCH_ADDR_SYS_PROP, dispatchAddr));
    }
  }

  private static void setupBuiltInTestEvents(@NotNull GradleExecutionSettings settings, @Nullable GradleVersion gradleVersion) {
    if (gradleVersion != null && GradleVersionUtil.isGradleAtLeast(gradleVersion, "7.6")) {
      settings.setBuiltInTestEventsUsed(true);
    }
  }

  public static void runCustomTaskScript(@NotNull Project project,
                                   @NotNull @Nls String executionName,
                                   @NotNull String projectPath,
                                   @NotNull String gradlePath,
                                   @NotNull ProgressExecutionMode progressExecutionMode,
                                   @Nullable TaskCallback callback,
                                   @NotNull String initScript,
                                   @NotNull String taskName) {
    UserDataHolderBase userData = new UserDataHolderBase();
    userData.putUserData(INIT_SCRIPT_KEY, initScript);
    userData.putUserData(ExternalSystemRunConfiguration.PROGRESS_LISTENER_KEY, SyncViewManager.class);

    String gradleVmOptions = GradleSettings.getInstance(project).getGradleVmOptions();
    ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
    settings.setExecutionName(executionName);
    settings.setExternalProjectPath(projectPath);
    String taskPrefix = gradlePath.endsWith(":") ? gradlePath : gradlePath + ':';
    settings.setTaskNames(Collections.singletonList(taskPrefix + taskName));
    settings.setVmOptions(gradleVmOptions);
    settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.getId());
    ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID, callback,
                               progressExecutionMode, false, userData);
  }

  public static void runCustomTask(@NotNull Project project,
                                   @NotNull @Nls String executionName,
                                   @NotNull Class<? extends Task> taskClass,
                                   @NotNull String projectPath,
                                   @NotNull String gradlePath,
                                   @Nullable String taskConfiguration,
                                   @NotNull ProgressExecutionMode progressExecutionMode,
                                   @Nullable TaskCallback callback,
                                   @NotNull Set<Class<?>> toolingExtensionClasses) {
    String taskName = taskClass.getSimpleName();
    String taskType = taskClass.getName();
    Set<Class<?>> tools = new HashSet<>(toolingExtensionClasses);
    tools.add(taskClass);
    tools.add(GsonBuilder.class);
    tools.add(ExternalSystemRtClass.class);
    tools.add(GradleToolingExtensionClass.class);
    tools.add(GradleToolingExtensionImplClass.class);
    String initScript = GradleInitScriptUtil.loadTaskInitScript(gradlePath, taskName, taskType, tools, taskConfiguration);
    runCustomTaskScript(project, executionName, projectPath, gradlePath, progressExecutionMode, callback, initScript, taskName);
  }

  public static void runCustomTask(@NotNull Project project,
                                   @NotNull @Nls String executionName,
                                   @NotNull Class<? extends Task> taskClass,
                                   @NotNull String projectPath,
                                   @NotNull String gradlePath,
                                   @Nullable String taskConfiguration,
                                   @NotNull ProgressExecutionMode progressExecutionMode,
                                   @Nullable TaskCallback callback) {
    runCustomTask(project, executionName, taskClass, projectPath, gradlePath, taskConfiguration, progressExecutionMode, callback,
                  new HashSet<>());
  }
}
