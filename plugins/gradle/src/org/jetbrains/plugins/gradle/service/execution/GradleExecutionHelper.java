// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetProgressIndicator;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider;
import com.intellij.openapi.externalSystem.util.OutputWrapper;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.task.RunConfigurationTaskState;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.gradle.api.logging.LogLevel;
import org.gradle.process.internal.JvmOptions;
import org.gradle.tooling.*;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.properties.GradleProperties;
import org.jetbrains.plugins.gradle.properties.GradlePropertiesFile;
import org.jetbrains.plugins.gradle.properties.models.Property;
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider;
import org.jetbrains.plugins.gradle.service.project.GradleOperationHelperExtension;
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLine;
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineOption;
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineTask;

import java.awt.geom.IllegalPathStateException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

import static org.jetbrains.plugins.gradle.GradleConnectorService.withGradleConnection;

public class GradleExecutionHelper {

  private static final Logger LOG = Logger.getInstance(GradleExecutionHelper.class);

  public <T> @NotNull ModelBuilder<T> getModelBuilder(
    @NotNull Class<T> modelType,
    @NotNull ProjectConnection connection,
    @NotNull ExternalSystemTaskId id,
    @NotNull GradleExecutionSettings settings,
    @NotNull ExternalSystemTaskNotificationListener listener
  ) {
    ModelBuilder<T> operation = connection.model(modelType);
    prepare(connection, operation, id, settings, listener);
    return operation;
  }

  public @NotNull BuildLauncher getBuildLauncher(
    @NotNull ProjectConnection connection,
    @NotNull ExternalSystemTaskId id,
    @NotNull List<String> tasksAndArguments,
    @NotNull GradleExecutionSettings settings,
    @NotNull ExternalSystemTaskNotificationListener listener
  ) {
    BuildLauncher operation = connection.newBuild();
    prepare(connection, operation, id, tasksAndArguments, settings, listener);
    return operation;
  }

  public @NotNull TestLauncher getTestLauncher(
    @NotNull ProjectConnection connection,
    @NotNull ExternalSystemTaskId id,
    @NotNull List<String> tasksAndArguments,
    @NotNull GradleExecutionSettings settings,
    @NotNull ExternalSystemTaskNotificationListener listener
  ) {
    var operation = connection.newTestLauncher();
    prepare(connection, operation, id, tasksAndArguments, settings, listener);
    return operation;
  }

  public <T> T execute(@NotNull String projectPath,
                       @Nullable GradleExecutionSettings settings,
                       @NotNull Function<? super ProjectConnection, ? extends T> f) {
    return execute(projectPath, settings, null, null, null, f);
  }

  public <T> T execute(@NotNull String projectPath,
                       @Nullable GradleExecutionSettings settings,
                       @Nullable ExternalSystemTaskId taskId,
                       @Nullable ExternalSystemTaskNotificationListener listener,
                       @Nullable CancellationTokenSource cancellationTokenSource,
                       @NotNull Function<? super ProjectConnection, ? extends T> f) {
    String projectDir;
    File projectPathFile = new File(projectPath);
    if (projectPathFile.isFile() && projectPath.endsWith(GradleConstants.EXTENSION) && projectPathFile.getParent() != null) {
      projectDir = projectPathFile.getParent();
      if (settings != null) {
        List<String> arguments = settings.getArguments();
        if (!arguments.contains("-b") && !arguments.contains("--build-file")) {
          settings.withArguments("-b", projectPath);
        }
      }
    }
    else {
      projectDir = projectPath;
    }
    CancellationToken cancellationToken = cancellationTokenSource != null ? cancellationTokenSource.token() : null;
    return withGradleConnection(
      projectDir, taskId, settings, listener, cancellationToken,
      connection -> {
        try {
          return maybeFixSystemProperties(() -> f.fun(connection), projectDir);
        }
        catch (ExternalSystemException | ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.warn("Gradle execution error", e);
          Throwable rootCause = ExceptionUtil.getRootCause(e);
          ExternalSystemException externalSystemException = new ExternalSystemException(ExceptionUtil.getMessage(rootCause), e);
          externalSystemException.initCause(e);
          throw externalSystemException;
        }
      });
  }

  private static <T> T maybeFixSystemProperties(@NotNull Computable<T> action, String projectDir) {
    Map<String, String> keyToMask = ApplicationManager.getApplication().getService(SystemPropertiesAdjuster.class).getKeyToMask(projectDir);
    Map<String, String> oldValues = new HashMap<>();
    try {
      keyToMask.forEach((key, newVal) -> {
        String oldVal = System.getProperty(key);
        oldValues.put(key, oldVal);
        if (oldVal != null) {
          SystemProperties.setProperty(key, newVal);
        }
      });
      return action.compute();
    }
    finally {
      // restore original properties
      oldValues.forEach((k, v) -> {
        if (v != null) {
          System.setProperty(k, v);
        }
      });
    }
  }

  /**
   * Use with caution! IDE system properties will be changed for the period of running Gradle long-running operations.
   * This is a workaround to fix leaking unwanted IDE system properties to Gradle process.
   */
  @ApiStatus.Internal
  public static class SystemPropertiesAdjuster {
    public SystemPropertiesAdjuster() {
      LOG.info("Gradle system adjuster service: " + this.getClass().getName());
    }

    public Map<String, String> getKeyToMask(@NotNull String projectDir) {
      Map<String, String> propertiesFixes = new HashMap<>();
      if (Registry.is("gradle.tooling.adjust.user.dir", true)) {
        propertiesFixes.put("user.dir", projectDir);
      }
      propertiesFixes.put("java.system.class.loader", null);
      propertiesFixes.put("jna.noclasspath", null);
      propertiesFixes.put("jna.boot.library.path", null);
      propertiesFixes.put("jna.nosys", null);
      return propertiesFixes;
    }
  }

  public void ensureInstalledWrapper(@NotNull ExternalSystemTaskId id,
                                     @NotNull String projectPath,
                                     @NotNull GradleExecutionSettings settings,
                                     @NotNull ExternalSystemTaskNotificationListener listener,
                                     @NotNull CancellationToken cancellationToken) {
    ensureInstalledWrapper(id, projectPath, settings, null, listener, cancellationToken);
  }

  public void ensureInstalledWrapper(@NotNull ExternalSystemTaskId id,
                                     @NotNull String projectPath,
                                     @NotNull GradleExecutionSettings settings,
                                     @Nullable GradleVersion gradleVersion,
                                     @NotNull ExternalSystemTaskNotificationListener listener,
                                     @NotNull CancellationToken cancellationToken) {
    if (!settings.getDistributionType().isWrapped()) {
      return;
    }
    if (settings.getDistributionType() == DistributionType.DEFAULT_WRAPPED &&
        GradleUtil.findDefaultWrapperPropertiesFile(projectPath) != null) {
      return;
    }
    withGradleConnection(projectPath, id, settings, listener, cancellationToken, connection -> {
      ensureInstalledWrapper(id, projectPath, settings, gradleVersion, listener, connection, cancellationToken);
      return null;
    });
  }

  private void ensureInstalledWrapper(@NotNull ExternalSystemTaskId id,
                                      @NotNull String projectPath,
                                      @NotNull GradleExecutionSettings settings,
                                      @Nullable GradleVersion gradleVersion,
                                      @NotNull ExternalSystemTaskNotificationListener listener,
                                      @NotNull ProjectConnection connection,
                                      @NotNull CancellationToken cancellationToken) {
    long ttlInMs = settings.getRemoteProcessIdleTtlInMs();
    try {
      settings.setRemoteProcessIdleTtlInMs(100);

      if (ExternalSystemExecutionAware.Companion.getEnvironmentConfigurationProvider(settings) != null) {
        // todo add the support for org.jetbrains.plugins.gradle.settings.DistributionType.WRAPPED
        executeWrapperTask(id, settings, projectPath, listener, connection, cancellationToken);

        Path wrapperPropertiesFile = GradleUtil.findDefaultWrapperPropertiesFile(projectPath);
        if (wrapperPropertiesFile != null) {
          settings.setWrapperPropertyFile(wrapperPropertiesFile.toString());
        }
      }
      else {
        Supplier<String> propertiesFile = setupWrapperTaskInInitScript(gradleVersion, settings);

        executeWrapperTask(id, settings, projectPath, listener, connection, cancellationToken);

        String wrapperPropertiesFile = propertiesFile.get();
        if (wrapperPropertiesFile != null) {
          settings.setWrapperPropertyFile(wrapperPropertiesFile);
        }
      }
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (IOException e) {
      LOG.warn("Can't update wrapper", e);
    }
    catch (Throwable e) {
      LOG.warn("Can't update wrapper", e);
      Throwable rootCause = ExceptionUtil.getRootCause(e);
      ExternalSystemException externalSystemException = new ExternalSystemException(ExceptionUtil.getMessage(rootCause));
      externalSystemException.initCause(e);
      throw externalSystemException;
    }
    finally {
      settings.setRemoteProcessIdleTtlInMs(ttlInMs);
      try {
        // if autoimport is active, it should be notified of new files creation as early as possible,
        // to avoid triggering unnecessary re-imports (caused by creation of wrapper)
        VfsUtil.markDirtyAndRefresh(false, true, true, Path.of(projectPath, "gradle").toFile());
      }
      catch (IllegalPathStateException ignore) {
      }
    }
  }

  private void executeWrapperTask(
    @NotNull ExternalSystemTaskId id,
    @NotNull GradleExecutionSettings settings,
    @NotNull String projectPath,
    @NotNull ExternalSystemTaskNotificationListener listener,
    @NotNull ProjectConnection connection,
    @NotNull CancellationToken cancellationToken
  ) {
    maybeFixSystemProperties(() -> {
      BuildLauncher launcher = getBuildLauncher(connection, id, List.of("wrapper"), settings, listener);
      launcher.withCancellationToken(cancellationToken);
      launcher.run();
      return null;
    }, projectPath);
  }

  private static @NotNull Supplier<String> setupWrapperTaskInInitScript(
    @Nullable GradleVersion gradleVersion,
    @NotNull GradleExecutionSettings settings
  ) throws IOException {
    File wrapperFilesLocation = FileUtil.createTempDirectory("wrap", "loc");
    File jarFile = new File(wrapperFilesLocation, "gradle-wrapper.jar");
    File scriptFile = new File(wrapperFilesLocation, "gradlew");
    File fileWithPathToProperties = new File(wrapperFilesLocation, "path.tmp");

    var initScriptFile = GradleInitScriptUtil.createWrapperInitScript(gradleVersion, jarFile, scriptFile, fileWithPathToProperties);
    settings.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, initScriptFile.toString());

    return () -> FileUtil.loadFileOrNull(fileWithPathToProperties);
  }

  @Nullable
  public static BuildEnvironment getBuildEnvironment(ProjectResolverContext projectResolverContext) {
    CancellationTokenSource cancellationTokenSource = projectResolverContext.getCancellationTokenSource();
    CancellationToken cancellationToken = cancellationTokenSource != null ? cancellationTokenSource.token() : null;
    return getBuildEnvironment(projectResolverContext.getConnection(),
                               projectResolverContext.getExternalSystemTaskId(),
                               projectResolverContext.getListener(),
                               cancellationToken,
                               projectResolverContext.getSettings());
  }

  public static void prepare(
    @NotNull ProjectConnection connection,
    @NotNull LongRunningOperation operation,
    @NotNull ExternalSystemTaskId id,
    @NotNull GradleExecutionSettings settings,
    @NotNull ExternalSystemTaskNotificationListener listener
  ) {
    prepare(connection, operation, id, Collections.emptyList(), settings, listener);
  }

  private static void prepare(
    @NotNull ProjectConnection connection,
    @NotNull LongRunningOperation operation,
    @NotNull ExternalSystemTaskId id,
    @NotNull List<String> tasksAndArguments,
    @NotNull GradleExecutionSettings settings,
    @NotNull ExternalSystemTaskNotificationListener listener
  ) {
    applyIdeaParameters(settings);

    BuildEnvironment buildEnvironment = getBuildEnvironment(connection, id, listener, settings);

    setupJvmArguments(operation, settings, buildEnvironment);

    setupLogging(settings, buildEnvironment);

    setupArguments(operation, tasksAndArguments, settings);

    setupEnvironment(operation, settings, id, listener, buildEnvironment);

    setupJavaHome(operation, settings);

    setupProgressListeners(operation, settings, id, listener, buildEnvironment);

    setupStandardIO(operation, settings, id, listener);

    GradleOperationHelperExtension.EP_NAME
      .forEachExtensionSafe(proc -> proc.prepareForExecution(id, operation, settings));
  }

  private static void applyIdeaParameters(@NotNull GradleExecutionSettings settings) {
    if (settings.isOfflineWork()) {
      settings.withArgument(GradleConstants.OFFLINE_MODE_CMD_OPTION);
    }
    settings.withArgument("-Didea.active=true");
    settings.withArgument("-Didea.version=" + getIdeaVersion());
  }

  private static void setupProgressListeners(
    @NotNull LongRunningOperation operation,
    @NotNull GradleExecutionSettings settings,
    @NotNull ExternalSystemTaskId id,
    @NotNull ExternalSystemTaskNotificationListener listener,
    @Nullable BuildEnvironment buildEnvironment
  ) {
    String buildRootDir = getBuildRoot(buildEnvironment);
    GradleProgressListener progressListener = new GradleProgressListener(listener, id, buildRootDir);
    operation.addProgressListener((ProgressListener)progressListener);
    operation.addProgressListener(progressListener, OperationType.TASK, OperationType.FILE_DOWNLOAD);
    if (settings.isRunAsTest() && settings.isBuiltInTestEventsUsed()) {
      operation.addProgressListener(progressListener, OperationType.TEST, OperationType.TEST_OUTPUT);
    }
  }

  private static void setupStandardIO(
    @NotNull LongRunningOperation operation,
    @NotNull GradleExecutionSettings settings,
    @NotNull ExternalSystemTaskId id,
    @NotNull ExternalSystemTaskNotificationListener listener
  ) {
    operation.setStandardOutput(new OutputWrapper(listener, id, true));
    operation.setStandardError(new OutputWrapper(listener, id, false));
    InputStream inputStream = settings.getUserData(ExternalSystemRunConfiguration.RUN_INPUT_KEY);
    if (inputStream != null) {
      operation.setStandardInput(inputStream);
    }
  }

  private static void setupJvmArguments(
    @NotNull LongRunningOperation operation,
    @NotNull GradleExecutionSettings settings,
    @Nullable BuildEnvironment buildEnvironment
  ) {
    List<String> jvmArgs = settings.getJvmArguments();
    if (!jvmArgs.isEmpty()) {
      // merge gradle args e.g. defined in gradle.properties
      Collection<String> merged;
      if (buildEnvironment != null) {
        // the BuildEnvironment jvm arguments of the main build should be used for the 'buildSrc' import
        // to avoid spawning of the second gradle daemon
        BuildIdentifier buildIdentifier = getBuildIdentifier(buildEnvironment);
        List<String> buildJvmArguments = buildIdentifier == null || "buildSrc".equals(buildIdentifier.getRootDir().getName())
                                         ? ContainerUtil.emptyList()
                                         : buildEnvironment.getJava().getJvmArguments();
        merged = mergeBuildJvmArguments(buildJvmArguments, jvmArgs);
      }
      else {
        merged = jvmArgs;
      }

      // filter nulls and empty strings
      List<String> filteredArgs = ContainerUtil.mapNotNull(merged, s -> StringUtil.isEmpty(s) ? null : s);

      operation.setJvmArguments(ArrayUtilRt.toStringArray(filteredArgs));
    }
  }

  private static void setupJavaHome(
    @NotNull LongRunningOperation operation,
    @NotNull GradleExecutionSettings settings
  ) {
    final String javaHome = settings.getJavaHome();
    if (javaHome != null && new File(javaHome).isDirectory()) {
      LOG.debug("Java home to set for Gradle operation: " + javaHome);
      operation.setJavaHome(new File(javaHome));
    }
  }

  private static void setupArguments(
    @NotNull LongRunningOperation operation,
    @NotNull List<String> tasksAndArguments,
    @NotNull GradleExecutionSettings settings
  ) {
    var commandLine = GradleCommandLineUtil.parseCommandLine(
      tasksAndArguments,
      settings.getArguments()
    );
    commandLine = fixUpGradleCommandLine(commandLine);

    LOG.info("Passing command-line to Gradle Tooling API: " +
             StringUtil.join(obfuscatePasswordParameters(commandLine.getTokens()), " "));

    if (operation instanceof TestLauncher testLauncher) {
      setupTestLauncherArguments(testLauncher, commandLine);
    }
    else if (operation instanceof BuildLauncher buildLauncher) {
      setupBuildLauncherArguments(buildLauncher, commandLine, settings);
    }
    else {
      operation.withArguments(commandLine.getTokens());
    }
  }

  private static @NotNull GradleCommandLine fixUpGradleCommandLine(@NotNull GradleCommandLine commandLine) {
    var tasks = new ArrayList<GradleCommandLineTask>();
    for (var task : commandLine.getTasks()) {
      var name = task.getName();
      var options = ContainerUtil.filter(task.getOptions(), it -> !isWildcardTestPattern(it));
      tasks.add(new GradleCommandLineTask(name, options));
    }
    return new GradleCommandLine(tasks, commandLine.getOptions());
  }

  private static boolean isWildcardTestPattern(@NotNull GradleCommandLineOption option) {
    return option.getName().equals(GradleConstants.TESTS_ARG_NAME) &&
           option.getValues().size() == 1 && (
             option.getValues().get(0).equals("*") ||
             option.getValues().get(0).equals("'*'") ||
             option.getValues().get(0).equals("\"*\"")
           );
  }

  private static void setupTestLauncherArguments(
    @NotNull TestLauncher testLauncher,
    @NotNull GradleCommandLine commandLine
  ) {
    for (var task : commandLine.getTasks()) {
      var patterns = GradleCommandLineUtil.getTestPatterns(task);
      if (!patterns.isEmpty()) {
        testLauncher.withTestsFor(
          it -> it.forTaskPath(task.getName())
            .includePatterns(patterns)
        );
      }
      else {
        testLauncher.forTasks(ArrayUtil.toStringArray(task.getTokens()));
      }
    }
    testLauncher.withArguments(commandLine.getOptions().getTokens());
  }

  private static void setupBuildLauncherArguments(
    @NotNull BuildLauncher buildLauncher,
    @NotNull GradleCommandLine commandLine,
    @NotNull GradleExecutionSettings settings
  ) {
    buildLauncher.forTasks(ArrayUtil.toStringArray(commandLine.getTasks().getTokens()));
    buildLauncher.withArguments(commandLine.getOptions().getTokens());
    if (settings.isTestTaskRerun()) {
      var initScript = GradleInitScriptUtil.createTestInitScript();
      buildLauncher.addArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, initScript.toString());
    }
  }

  private static void setupLogging(@NotNull GradleExecutionSettings settings,
                                   @Nullable BuildEnvironment buildEnvironment) {
    var arguments = settings.getArguments();
    var options = GradleCommandLineOptionsProvider.LOGGING_OPTIONS.getOptions();
    var optionsNames = GradleCommandLineOptionsProvider.getAllOptionsNames(options);

    // workaround for https://github.com/gradle/gradle/issues/19340
    // when using TAPI, user-defined log level option in gradle.properties is ignored by Gradle.
    // try to read this file manually and apply log level explicitly
    if (buildEnvironment == null || !isRootDirAvailable(buildEnvironment)) {
      return;
    }
    GradleProperties properties = GradlePropertiesFile.INSTANCE.getProperties(settings.getServiceDirectory(),
                                                                              buildEnvironment.getBuildIdentifier().getRootDir().toPath());
    Property<String> loggingLevelProperty = properties.getGradleLoggingLevel();
    @NonNls String gradleLogLevel = loggingLevelProperty != null ? loggingLevelProperty.getValue() : null;

    if (!ContainerUtil.exists(optionsNames, it -> arguments.contains(it))
        && gradleLogLevel != null) {
      try {
        LogLevel logLevel = LogLevel.valueOf(gradleLogLevel.toUpperCase());
        switch (logLevel) {
          case DEBUG -> settings.withArgument("-d");
          case INFO -> settings.withArgument("-i");
          case WARN -> settings.withArgument("-w");
          case QUIET -> settings.withArgument("-q");
        }
      }
      catch (IllegalArgumentException e) {
        LOG.warn("org.gradle.logging.level must be one of quiet, warn, lifecycle, info, or debug");
      }
    }

    // Default logging level for integration tests
    final Application application = ApplicationManager.getApplication();
    if (application != null && application.isUnitTestMode()) {
      if (!ContainerUtil.exists(optionsNames, it -> arguments.contains(it))) {
        settings.withArgument("--info");
      }
    }
  }

  private static boolean isRootDirAvailable(@NotNull BuildEnvironment environment) {
    try {
      environment.getBuildIdentifier().getRootDir();
    }
    catch (UnsupportedMethodException e) {
      return false;
    }
    return true;
  }

  @Nullable
  private static BuildIdentifier getBuildIdentifier(@NotNull BuildEnvironment buildEnvironment) {
    try {
      return buildEnvironment.getBuildIdentifier();
    }
    catch (UnsupportedMethodException ignore) {
    }
    return null;
  }

  private static @Nullable String getBuildRoot(@Nullable BuildEnvironment buildEnvironment) {
    if (buildEnvironment == null) {
      return null;
    }
    BuildIdentifier buildIdentifier = getBuildIdentifier(buildEnvironment);
    return buildIdentifier == null ? null : buildIdentifier.getRootDir().getPath();
  }

  private static void setupEnvironment(
    @NotNull LongRunningOperation operation,
    @NotNull GradleExecutionSettings settings,
    @NotNull ExternalSystemTaskId taskId,
    @NotNull ExternalSystemTaskNotificationListener listener,
    @Nullable BuildEnvironment buildEnvironment
  ) {
    String gradleVersion = buildEnvironment != null ? buildEnvironment.getGradle().getGradleVersion() : null;
    boolean isEnvironmentCustomizationSupported =
      gradleVersion != null && GradleVersion.version(gradleVersion).getBaseVersion().compareTo(GradleVersion.version("3.5")) >= 0;
    if (!isEnvironmentCustomizationSupported) {
      if (!settings.isPassParentEnvs() || !settings.getEnv().isEmpty()) {
        listener.onTaskOutput(taskId, String.format(
          "The version of Gradle you are using%s does not support the environment variables customization feature. " +
          "Support for this is available in Gradle 3.5 and all later versions.\n",
          gradleVersion == null ? "" : (" (" + gradleVersion + ")")), false);
      }
      return;
    }

    TargetEnvironmentConfigurationProvider environmentConfigurationProvider =
      ExternalSystemExecutionAware.Companion.getEnvironmentConfigurationProvider(settings);
    TargetEnvironmentConfiguration environmentConfiguration =
      environmentConfigurationProvider != null ? environmentConfigurationProvider.getEnvironmentConfiguration() : null;
    if (environmentConfiguration != null && !LocalGradleExecutionAware.LOCAL_TARGET_TYPE_ID.equals(environmentConfiguration.getTypeId())) {
      if (settings.isPassParentEnvs()) {
        LOG.warn("Host system environment variables will not be passed for the target run.");
      }
      operation.setEnvironmentVariables(settings.getEnv());
      return;
    }

    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.withEnvironment(settings.getEnv());
    commandLine.withParentEnvironmentType(
      settings.isPassParentEnvs() ? GeneralCommandLine.ParentEnvironmentType.CONSOLE : GeneralCommandLine.ParentEnvironmentType.NONE);
    Map<String, String> effectiveEnvironment = commandLine.getEffectiveEnvironment();
    operation.setEnvironmentVariables(effectiveEnvironment);
  }

  @ApiStatus.Internal
  static List<String> mergeBuildJvmArguments(@NotNull List<String> jvmArgs, @NotNull List<String> jvmArgsFromIdeSettings) {
    List<String> mergedJvmArgs = mergeJvmArgs(jvmArgs, jvmArgsFromIdeSettings);
    JvmOptions jvmOptions = new JvmOptions(null);
    jvmOptions.setAllJvmArgs(mergedJvmArgs);
    return jvmOptions.getAllJvmArgs();
  }

  @ApiStatus.Internal
  static List<String> mergeJvmArgs(@NotNull List<String> jvmArgs, @NotNull List<String> jvmArgsFromIdeSettings) {
    MultiMap<String, String> argumentsMap = MultiMap.createLinkedSet();
    String lastKey = null;
    for (String jvmArg : ContainerUtil.concat(jvmArgs, jvmArgsFromIdeSettings)) {
      if (jvmArg.startsWith("-")) {
        argumentsMap.putValue(jvmArg, "");
        lastKey = jvmArg;
      }
      else {
        if (lastKey != null) {
          argumentsMap.putValue(lastKey, jvmArg);
          lastKey = null;
        }
        else {
          argumentsMap.putValue(jvmArg, "");
        }
      }
    }

    Map<String, String> mergedKeys = new LinkedHashMap<>();
    Set<String> argKeySet = new LinkedHashSet<>(argumentsMap.keySet());
    for (String argKey : argKeySet) {
      Collection<String> values = argumentsMap.getModifiable(argKey);
      if (values.size() == 1 && values.iterator().next().isEmpty()) {
        Couple<String> couple = splitArg(argKey);
        mergedKeys.put(couple.first, couple.second);
      }
      else {
        mergedKeys.put(argKey, "");
        Map<String, String> mergedArgs = new LinkedHashMap<>();
        for (String jvmArg : values) {
          if (jvmArg.isEmpty()) continue;
          Couple<String> couple = splitArg(jvmArg);
          mergedArgs.put(couple.first, couple.second);
        }
        values.clear();
        mergedArgs.forEach((key, value) -> values.add(key + value));
      }
    }

    List<String> mergedArgs = new SmartList<>();
    mergedKeys.forEach((s1, s2) -> mergedArgs.add(s1 + s2));
    argKeySet.stream().filter(argKey -> !mergedArgs.contains(argKey)).forEach(argumentsMap::remove);

    // remove `--add-opens` options, because same options will be added by gradle producing the option duplicates.
    // And the daemon will become uncompilable with the CLI invocations.
    // see https://github.com/gradle/gradle/blob/v5.1.1/subprojects/launcher/src/main/java/org/gradle/launcher/daemon/configuration/DaemonParameters.java#L125
    argumentsMap.remove("--add-opens");

    List<String> result = new SmartList<>();
    argumentsMap.keySet().forEach(key -> argumentsMap.get(key).forEach(val -> {
      result.add(key);
      if (StringUtil.isNotEmpty(val)) {
        result.add(val);
      }
    }));
    return result;
  }

  private static Couple<String> splitArg(String arg) {
    int i = arg.indexOf('=');
    return i <= 0 ? Couple.of(arg, "") : Couple.of(arg.substring(0, i), arg.substring(i));
  }

  @ApiStatus.Internal
  public static void attachTargetPathMapperInitScript(@NotNull GradleExecutionSettings executionSettings) {
    var initScriptFile = GradleInitScriptUtil.createTargetPathMapperInitScript();
    executionSettings.prependArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, initScriptFile.toString());
  }

  @ApiStatus.Experimental
  @NotNull
  public static Map<String, String> getConfigurationInitScripts(@NonNls GradleRunConfiguration configuration) {
    final String initScript = configuration.getUserData(GradleTaskManager.INIT_SCRIPT_KEY);
    if (StringUtil.isNotEmpty(initScript)) {
      String prefix = Objects.requireNonNull(
        configuration.getUserData(GradleTaskManager.INIT_SCRIPT_PREFIX_KEY),
        "init script file prefix is required"
      );
      Map<String, String> map = new LinkedHashMap<>();
      map.put(prefix, initScript);
      String taskStateInitScript = getTaskStateInitScript(configuration);
      if (taskStateInitScript != null) {
        map.put("ijtgttaskstate", taskStateInitScript);
      }
      return map;
    }
    return Collections.emptyMap();
  }

  private static @Nullable String getTaskStateInitScript(@NonNls GradleRunConfiguration configuration) {
    RunConfigurationTaskState taskState = configuration.getUserData(RunConfigurationTaskState.getKEY());
    if (taskState == null) return null;

    LocalTargetEnvironmentRequest request = new LocalTargetEnvironmentRequest();
    TargetProgressIndicator progressIndicator = TargetProgressIndicator.EMPTY;
    try {
      taskState.prepareTargetEnvironmentRequest(request, progressIndicator);
      LocalTargetEnvironment environment = request.prepareEnvironment(progressIndicator);
      return taskState.handleCreatedTargetEnvironment(environment, progressIndicator);
    }
    catch (ExecutionException e) {
      return null;
    }
  }

  @Nullable
  public static GradleVersion getGradleVersion(@NotNull ProjectConnection connection,
                                               @NotNull ExternalSystemTaskId taskId,
                                               @NotNull ExternalSystemTaskNotificationListener listener,
                                               @Nullable CancellationTokenSource cancellationTokenSource) {
    final BuildEnvironment buildEnvironment = getBuildEnvironment(connection, taskId, listener, cancellationTokenSource, null);

    GradleVersion gradleVersion = null;
    if (buildEnvironment != null) {
      gradleVersion = GradleVersion.version(buildEnvironment.getGradle().getGradleVersion());
    }
    return gradleVersion;
  }

  @Nullable
  public static BuildEnvironment getBuildEnvironment(@NotNull ProjectConnection connection,
                                                     @NotNull ExternalSystemTaskId taskId,
                                                     @NotNull ExternalSystemTaskNotificationListener listener,
                                                     @Nullable CancellationTokenSource cancellationTokenSource,
                                                     @Nullable GradleExecutionSettings settings) {
    CancellationToken cancellationToken = cancellationTokenSource != null ? cancellationTokenSource.token() : null;
    return getBuildEnvironment(connection, taskId, listener, cancellationToken, settings);
  }

  private static @Nullable BuildEnvironment getBuildEnvironment(
    @NotNull ProjectConnection connection,
    @NotNull ExternalSystemTaskId taskId,
    @NotNull ExternalSystemTaskNotificationListener listener,
    @Nullable GradleExecutionSettings settings
  ) {
    return getBuildEnvironment(connection, taskId, listener, (CancellationToken)null, settings);
  }

  @Nullable
  public static BuildEnvironment getBuildEnvironment(@NotNull ProjectConnection connection,
                                                     @NotNull ExternalSystemTaskId taskId,
                                                     @NotNull ExternalSystemTaskNotificationListener listener,
                                                     @Nullable CancellationToken cancellationToken,
                                                     @Nullable GradleExecutionSettings settings) {
    BuildEnvironment buildEnvironment = null;
    try {
      ModelBuilder<BuildEnvironment> modelBuilder = connection.model(BuildEnvironment.class);
      if (cancellationToken != null) {
        modelBuilder.withCancellationToken(cancellationToken);
      }
      if (settings != null) {
        final String javaHome = settings.getJavaHome();
        if (javaHome != null && new File(javaHome).isDirectory()) {
          modelBuilder.setJavaHome(new File(javaHome));
        }
      }
      // do not use connection.getModel methods since it doesn't allow to handle progress events
      // and we can miss gradle tooling client side events like distribution download.
      GradleProgressListener gradleProgressListener = new GradleProgressListener(listener, taskId);
      modelBuilder.addProgressListener((ProgressListener)gradleProgressListener);
      modelBuilder.addProgressListener((org.gradle.tooling.events.ProgressListener)gradleProgressListener);
      modelBuilder.setStandardOutput(new OutputWrapper(listener, taskId, true));
      modelBuilder.setStandardError(new OutputWrapper(listener, taskId, false));

      buildEnvironment = modelBuilder.get();
      if (LOG.isDebugEnabled()) {
        try {
          String version = buildEnvironment.getGradle().getGradleVersion();
          LOG.debug("Gradle version: " + version);
          if (GradleVersion.version(version).compareTo(GradleVersion.version("2.6")) >= 0) {
            LOG.debug("Gradle java home: " + buildEnvironment.getJava().getJavaHome());
            LOG.debug("Gradle jvm arguments: " + buildEnvironment.getJava().getJvmArguments());
          }
        }
        catch (Throwable t) {
          LOG.debug(t);
        }
      }
    }
    catch (Throwable t) {
      LOG.warn("Failed to obtain build environment from Gradle daemon.", t);
    }
    return buildEnvironment;
  }

  public static @NotNull Set<String> getToolingExtensionsJarPaths(@NotNull Set<Class<?>> toolingExtensionClasses) {
    return ContainerUtil.map2SetNotNull(toolingExtensionClasses, aClass -> {
      String path = PathManager.getJarPathForClass(aClass);
      if (path != null) {
        if (FileUtilRt.getNameWithoutExtension(path).equals("gradle-api-" + GradleVersion.current().getBaseVersion())) {
          LOG.warn("The gradle api jar shouldn't be added to the gradle daemon classpath: {" + aClass + "," + path + "}");
          return null;
        }
        if (FileUtil.normalize(path).endsWith("lib/app.jar")) {
          final String message = "Attempting to pass whole IDEA app [" + path + "] into Gradle Daemon for class [" + aClass + "]";
          if (ApplicationManagerEx.isInIntegrationTest()) {
            LOG.error(message);
          }
          else {
            LOG.warn(message);
          }
        }
        return FileUtil.toCanonicalPath(path);
      }
      return null;
    });
  }

  @NotNull
  static List<String> obfuscatePasswordParameters(@NotNull List<String> commandLineArguments) {
    List<String> replaced = new ArrayList<>(commandLineArguments.size());
    final String PASSWORD_PARAMETER_IDENTIFIER = ".password=";
    for (String option : commandLineArguments) {
      // Find parameters ending in "password", like:
      //   -Pandroid.injected.signing.store.password=
      //   -Pandroid.injected.signing.key.password=
      int index = option.indexOf(PASSWORD_PARAMETER_IDENTIFIER);
      if (index == -1) {
        replaced.add(option);
      }
      else {
        replaced.add(option.substring(0, index + PASSWORD_PARAMETER_IDENTIFIER.length()) + "*********");
      }
    }
    return replaced;
  }

  private static String getIdeaVersion() {
    ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
    return appInfo.getMajorVersion() + "." + appInfo.getMinorVersion();
  }
}
