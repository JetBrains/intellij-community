// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.util.ExternalSystemTelemetryUtil;
import com.intellij.openapi.externalSystem.util.OutputWrapper;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.gradle.tooling.*;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.gradle.connection.GradleConnectorService;
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix;
import org.jetbrains.plugins.gradle.properties.GradlePropertiesFile;
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider;
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelperExtension;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLine;
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineTask;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * This is the low-level Gradle execution API that connects and interacts with the Gradle daemon using the Gradle tooling API.
 * <p>
 * Consider using the high-level Gradle execution APIs instead:
 * <ul>
 * <li>{@link com.intellij.openapi.externalSystem.util.ExternalSystemUtil#runTask}</li>
 * <li>{@link com.intellij.openapi.externalSystem.util.task.TaskExecutionUtil#runTask}</li>
 * </ul>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/tooling_api.html">Gradle tooling API</a>
 */
@ApiStatus.Internal
public final class GradleExecutionHelper {

  /**
   * @deprecated Use helper methods without object instantiation.
   * All methods in this class are static.
   */
  @Deprecated
  public GradleExecutionHelper() { }

  private static final Logger LOG = Logger.getInstance(GradleExecutionHelper.class);

  // do not use
  // this flag is used only for the situation where is not possible to execute a Gradle task in an appropriate way and we have to use
  // the bundled JDK for the execution
  public static final Key<Boolean> AUTO_JAVA_HOME = Key.create("AUTO_JAVA_HOME");

  /**
   * @deprecated Use the {@link ProjectConnection#newBuild} function directly.
   * Or use the {@link com.intellij.openapi.externalSystem.util.ExternalSystemUtil#runTask} API for the high-level Gradle task execution.
   */
  @Deprecated
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

  /**
   * @deprecated Use the {@link ProjectConnection#newTestLauncher} function directly.
   * Or use the {@link com.intellij.openapi.externalSystem.util.ExternalSystemUtil#runTask} API for the high-level Gradle task execution.
   */
  @Deprecated
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

  /**
   * @deprecated Existed for the {@link #getBuildLauncher} and {@link #getTestLauncher} functions.
   */
  @Deprecated
  private static void prepare(
    @NotNull ProjectConnection connection,
    @NotNull LongRunningOperation operation,
    @NotNull ExternalSystemTaskId id,
    @NotNull List<String> tasksAndArguments,
    @NotNull GradleExecutionSettings settings,
    @NotNull ExternalSystemTaskNotificationListener listener
  ) {
    GradleExecutionSettings effectiveSettings = new GradleExecutionSettings(settings);
    effectiveSettings.setTasks(ContainerUtil.concat(effectiveSettings.getTasks(), tasksAndArguments));
    CancellationToken cancellationToken = GradleConnector.newCancellationTokenSource().token();
    GradleExecutionContextImpl context = new GradleExecutionContextImpl("", id, effectiveSettings, listener, cancellationToken);
    BuildEnvironment buildEnvironment = getBuildEnvironment(connection, context);
    context.setBuildEnvironment(buildEnvironment);
    prepareForExecution(operation, context);
  }

  /**
   * @deprecated use the {@link GradleExecutionHelper#execute} function with {@link GradleExecutionContext} instead.
   * The {@link BuildEnvironment} model will be automatically provided to {@link GradleExecutionContext}.
   */
  @Deprecated
  public static @NotNull BuildEnvironment getBuildEnvironment(
    @NotNull ProjectConnection connection,
    @NotNull ExternalSystemTaskId taskId,
    @NotNull ExternalSystemTaskNotificationListener listener,
    @Nullable CancellationToken cancellationToken,
    @Nullable GradleExecutionSettings settings
  ) {
    GradleExecutionSettings effectiveSettings = ObjectUtils.notNull(settings, () ->
      new GradleExecutionSettings()
    );
    CancellationToken effectiveCancellationToken = ObjectUtils.notNull(cancellationToken, () ->
      GradleConnector.newCancellationTokenSource().token()
    );
    GradleExecutionContextImpl context = new GradleExecutionContextImpl(
      "", taskId, effectiveSettings, listener, effectiveCancellationToken
    );
    return getBuildEnvironment(connection, context);
  }

  /**
   * @deprecated use the {@link GradleExecutionHelper#execute} function with {@link GradleExecutionContext} instead
   */
  @Deprecated
  public <T> T execute(
    @NotNull String projectPath,
    @Nullable GradleExecutionSettings settings,
    @NotNull Function<? super ProjectConnection, ? extends T> f
  ) {
    return execute(projectPath, settings, null, null, null, f);
  }

  /**
   * @deprecated use the {@link GradleExecutionHelper#execute} function with {@link GradleExecutionContext} instead
   */
  @Deprecated
  public static <T> T execute(
    @NotNull String projectPath,
    @Nullable GradleExecutionSettings settings,
    @Nullable ExternalSystemTaskId taskId,
    @Nullable ExternalSystemTaskNotificationListener listener,
    @Nullable CancellationToken cancellationToken,
    @NotNull Function<? super ProjectConnection, ? extends T> f
  ) {
    String projectDir;
    //noinspection IO_FILE_USAGE
    File projectPathFile = new File(projectPath);
    if (Files.isRegularFile(Path.of(projectPath)) &&
        projectPath.endsWith(GradleConstants.EXTENSION) &&
        projectPathFile.getParent() != null) {
      projectDir = projectPathFile.getParent();
      if (settings != null) {
        List<String> arguments = settings.getArguments();
        // Setting the custom build file location is deprecated since Gradle 7.6, see IDEA-359161 for more details.
        if (!arguments.contains("-b") && !arguments.contains("--build-file")) {
          settings.withArguments("-b", projectPath);
        }
      }
    }
    else {
      projectDir = projectPath;
    }
    GradleConnectorService connectorService = GradleConnectorService.getInstance(projectDir, taskId);
    return connectorService.withGradleConnection(projectDir, taskId, settings, listener, cancellationToken, connection -> {
      try {
        return SystemPropertiesAdjuster.executeAdjusted(projectDir, () -> f.fun(connection));
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

  public static <T> T execute(
    @NotNull GradleExecutionContextImpl context,
    @NotNull java.util.function.Function<? super ProjectConnection, ? extends T> action
  ) {
    return execute(
      context.getProjectPath(), context.getSettings(), context.getTaskId(), context.getListener(), context.getCancellationToken(),
      connection -> {
        BuildEnvironment buildEnvironment = null;
        try {
          buildEnvironment = getBuildEnvironment(connection, context);
          context.setBuildEnvironment(buildEnvironment);
          return action.apply(connection);
        }
        catch (CancellationException ce) {
          throw ce;
        }
        catch (Exception ex) {
          throw GradleProjectResolver.createProjectResolverChain()
            .getUserFriendlyError(buildEnvironment, ex, context.getProjectPath(), null);
        }
      });
  }

  public static void prepareForExecution(
    @NotNull LongRunningOperation operation,
    @NotNull GradleExecutionContext context
  ) {
    var id = context.getTaskId();
    var settings = context.getSettings();
    var listener = context.getListener();
    var buildEnvironment = context.getBuildEnvironment();

    applyIdeaParameters(settings);

    setupLogging(settings, buildEnvironment);

    GradleExecutionHelperExtension.EP_NAME.forEachExtensionSafe(proc -> {
      proc.configureSettings(settings, context);
    });

    clearSystemProperties(operation);

    setupJvmArguments(operation, settings);

    setupArguments(operation, settings);

    setupEnvironment(operation, settings);

    setupJavaHome(operation, settings, id, listener, buildEnvironment);

    setupProgressListeners(operation, settings, id, listener, buildEnvironment);

    setupStandardIO(operation, settings, id, listener);

    operation.withCancellationToken(context.getCancellationToken());

    GradleExecutionHelperExtension.EP_NAME.forEachExtensionSafe(proc -> {
      proc.configureOperation(operation, context);
    });
  }

  private static void clearSystemProperties(LongRunningOperation operation) {
    // for Gradle 7.6+ this will cancel implicit transfer of current System.properties to Gradle Daemon.
    operation.withSystemProperties(Collections.emptyMap());
  }

  private static void applyIdeaParameters(@NotNull GradleExecutionSettings settings) {
    if (settings.isOfflineWork()) {
      settings.withArgument(GradleConstants.OFFLINE_MODE_CMD_OPTION);
    }
    settings.withArgument("-Didea.active=true");
    settings.withArgument("-Didea.version=" + getIdeaVersion());
    settings.withArgument("-Didea.vendor.name=" + ApplicationInfo.getInstance().getShortCompanyName());
  }

  private static void setupProgressListeners(
    @NotNull LongRunningOperation operation,
    @NotNull GradleExecutionSettings settings,
    @NotNull ExternalSystemTaskId id,
    @NotNull ExternalSystemTaskNotificationListener listener,
    @Nullable BuildEnvironment buildEnvironment
  ) {
    var buildRootDir = getBuildRoot(buildEnvironment);
    var progressListener = new GradleProgressListener(listener, id, buildRootDir);
    operation.addProgressListener((ProgressListener)progressListener);
    operation.addProgressListener(
      progressListener,
      OperationType.TASK,
      OperationType.FILE_DOWNLOAD
    );
    if (settings.isRunAsTest() && settings.isBuiltInTestEventsUsed()) {
      operation.addProgressListener(
        progressListener,
        OperationType.TEST,
        OperationType.TEST_OUTPUT,
        OperationType.TASK
      );
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

  @VisibleForTesting
  public static void setupJvmArguments(
    @NotNull LongRunningOperation operation,
    @NotNull GradleExecutionSettings settings
  ) {
    var jvmArgs = ContainerUtil.filter(settings.getJvmArguments(), it -> !StringUtil.isEmpty(it));
    if (!jvmArgs.isEmpty()) {
      operation.addJvmArguments(ArrayUtilRt.toStringArray(jvmArgs));
    }
  }

  private static void setupJavaHome(
    @NotNull LongRunningOperation operation,
    @NotNull GradleExecutionSettings settings,
    @NotNull ExternalSystemTaskId id,
    @NotNull ExternalSystemTaskNotificationListener listener,
    @Nullable BuildEnvironment buildEnvironment
  ) {
    var javaHome = getJavaHomeForOperation(settings, id, listener, buildEnvironment);
    if (javaHome == null) {
      return;
    }
    //noinspection IO_FILE_USAGE
    operation.setJavaHome(new File(javaHome));
    LOG.debug("Java home to set for Gradle operation: " + javaHome);
  }

  private static @Nullable String getJavaHomeForOperation(
    @NotNull GradleExecutionSettings settings,
    @NotNull ExternalSystemTaskId id,
    @NotNull ExternalSystemTaskNotificationListener listener,
    @Nullable BuildEnvironment buildEnvironment
  ) {
    if (Boolean.TRUE.equals(settings.getUserData(AUTO_JAVA_HOME)) && buildEnvironment != null) {
      var project = id.getProject();
      var gradle = buildEnvironment.getGradle();
      var gradleVersion = GradleVersion.version(gradle.getGradleVersion());

      for (var sdkPath : ExternalSystemJdkUtil.suggestJdkHomePaths(project)) {
        var javaVersion = ExternalSystemJdkUtil.getJavaVersion(sdkPath);
        if (javaVersion != null && GradleJvmSupportMatrix.isSupported(gradleVersion, javaVersion)) {
          listener.onTaskOutput(
            id,
            GradleBundle.message("gradle.auto.jdk.was.selected", sdkPath) + System.lineSeparator(),
            true
          );
          return sdkPath;
        }
      }
    }
    return settings.getJavaHome();
  }

  private static void setupArguments(
    @NotNull LongRunningOperation operation,
    @NotNull GradleExecutionSettings settings
  ) {
    var commandLine = fixUpGradleCommandLine(settings.getCommandLine());

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
      var options = ContainerUtil.filter(task.getOptions(), it -> !GradleCommandLineUtil.isWildcardTestPattern(it));
      tasks.add(new GradleCommandLineTask(name, options));
    }
    return new GradleCommandLine(tasks, commandLine.getOptions());
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

  @VisibleForTesting
  @SuppressWarnings("ConstantValue") //Qodana false positive
  public static void setupLogging(
    @NotNull GradleExecutionSettings settings,
    @Nullable BuildEnvironment buildEnvironment
  ) {
    var arguments = settings.getArguments();
    var options = GradleCommandLineOptionsProvider.LOGGING_OPTIONS.getOptions();
    var optionsNames = GradleCommandLineOptionsProvider.getAllOptionsNames(options);

    if (ContainerUtil.exists(optionsNames, it -> arguments.contains(it))) {
      return;
    }

    // workaround for https://github.com/gradle/gradle/issues/19340
    // when using TAPI, user-defined log level option in gradle.properties is ignored by Gradle.
    // try to read this file manually and apply log level explicitly
    var buildRoot = getBuildRoot(buildEnvironment);
    if (buildRoot != null) {
      var properties = GradlePropertiesFile.getProperties(settings.getServiceDirectory(), buildRoot);
      var logLevel = properties.getGradleLogLevel();
      if (logLevel != null) {
        switch (logLevel) {
          case DEBUG -> settings.withArgument("-d");
          case INFO -> settings.withArgument("-i");
          case WARN -> settings.withArgument("-w");
          case QUIET -> settings.withArgument("-q");
        }
      }
    }

    if (ContainerUtil.exists(optionsNames, it -> arguments.contains(it))) {
      return;
    }

    // Default logging level for integration tests
    final Application application = ApplicationManager.getApplication();
    if (application != null && application.isUnitTestMode()) {
      settings.withArgument("--info");
    }
  }

  private static @Nullable Path getBuildRoot(@Nullable BuildEnvironment buildEnvironment) {
    if (buildEnvironment == null) {
      return null;
    }
    return buildEnvironment.getBuildIdentifier().getRootDir().toPath();
  }

  private static void setupEnvironment(
    @NotNull LongRunningOperation operation,
    @NotNull GradleExecutionSettings settings
  ) {
    var environmentConfigurationProvider = ExternalSystemExecutionAware.getEnvironmentConfigurationProvider(settings);
    var environmentConfiguration = ObjectUtils.doIfNotNull(environmentConfigurationProvider, it -> it.getEnvironmentConfiguration());
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

  /**
   * Visible for {@link com.android.tools.idea.gradle.project.build.invoker.GradleTasksExecutorImpl}.
   */
  public static @NotNull BuildEnvironment getBuildEnvironment(
    @NotNull ProjectConnection connection,
    @NotNull GradleExecutionContext context
  ) {
    Span span = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)
      .spanBuilder("GetBuildEnvironment")
      .startSpan();
    try (Scope ignore = span.makeCurrent()) {
      BuildEnvironment buildEnvironment;
      try {
        ExternalSystemTaskId taskId = context.getTaskId();
        ExternalSystemTaskNotificationListener listener = context.getListener();

        ModelBuilder<BuildEnvironment> modelBuilder = connection.model(BuildEnvironment.class);

        modelBuilder.withCancellationToken(context.getCancellationToken());

        setupJavaHome(modelBuilder, context.getSettings(), taskId, listener, null);

        // do not use connection.getModel methods since it doesn't allow to handle progress events
        // and we can miss gradle tooling client side events like distribution download.
        GradleProgressListener gradleProgressListener = new GradleProgressListener(listener, taskId);
        modelBuilder.addProgressListener((ProgressListener)gradleProgressListener);
        modelBuilder.addProgressListener((org.gradle.tooling.events.ProgressListener)gradleProgressListener);
        modelBuilder.setStandardOutput(new OutputWrapper(listener, taskId, true));
        modelBuilder.setStandardError(new OutputWrapper(listener, taskId, false));

        buildEnvironment = modelBuilder.get();
      }
      catch (Exception ex) {
        throw new RuntimeException("Failed to obtain build environment from Gradle daemon.", ex);
      }

      checkThatGradleBuildEnvironmentIsSupportedByIdea(buildEnvironment);

      return buildEnvironment;
    }
    catch (CancellationException ce) {
      throw ce;
    }
    catch (Exception ex) {
      span.recordException(ex);
      span.setStatus(StatusCode.ERROR);
      throw ex;
    }
    finally {
      span.end();
    }
  }

  private static void checkThatGradleBuildEnvironmentIsSupportedByIdea(@NotNull BuildEnvironment buildEnvironment) {
    var gradleVersion = GradleVersion.version(buildEnvironment.getGradle().getGradleVersion());
    LOG.debug("Gradle version: " + gradleVersion);
    if (!GradleJvmSupportMatrix.isGradleSupportedByIdea(gradleVersion)) {
      throw new UnsupportedGradleVersionByIdeaException(gradleVersion);
    }
    var javaHome = buildEnvironment.getJava().getJavaHome();
    var jvmArguments = buildEnvironment.getJava().getJvmArguments();
    LOG.debug("Gradle java home: " + javaHome);
    LOG.debug("Gradle jvm arguments: " + jvmArguments);
    var javaVersion = ExternalSystemJdkUtil.getJavaVersion(javaHome.getPath());
    if (javaVersion != null && !GradleJvmSupportMatrix.isJavaSupportedByIdea(javaVersion)) {
      throw new UnsupportedGradleJvmByIdeaException(gradleVersion, javaVersion);
    }
  }

  public static class UnsupportedGradleVersionByIdeaException extends RuntimeException {

    private final @NotNull GradleVersion myGradleVersion;

    public UnsupportedGradleVersionByIdeaException(@NotNull GradleVersion gradleVersion) {
      super("Unsupported Gradle version");
      myGradleVersion = gradleVersion;
    }

    public @NotNull GradleVersion getGradleVersion() {
      return myGradleVersion;
    }
  }

  public static class UnsupportedGradleJvmByIdeaException extends RuntimeException {

    private final @NotNull GradleVersion myGradleVersion;
    private final @Nullable JavaVersion myJavaVersion;

    public UnsupportedGradleJvmByIdeaException(
      @NotNull GradleVersion gradleVersion,
      @Nullable JavaVersion javaVersion
    ) {
      super("Unsupported Gradle JVM version");
      myGradleVersion = gradleVersion;
      myJavaVersion = javaVersion;
    }

    public @NotNull GradleVersion getGradleVersion() {
      return myGradleVersion;
    }

    public @Nullable JavaVersion getJavaVersion() {
      return myJavaVersion;
    }
  }

  @VisibleForTesting
  public static @NotNull List<String> obfuscatePasswordParameters(@NotNull List<String> commandLineArguments) {
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
