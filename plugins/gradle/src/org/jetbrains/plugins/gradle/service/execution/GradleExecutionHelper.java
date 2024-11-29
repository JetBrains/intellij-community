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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.lang.JavaVersion;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.gradle.tooling.*;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.*;
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix;
import org.jetbrains.plugins.gradle.properties.GradlePropertiesFile;
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider;
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelperExtension;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLine;
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineOption;
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineTask;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

import static org.jetbrains.plugins.gradle.GradleConnectorService.withGradleConnection;

public final class GradleExecutionHelper {

  /**
   * @deprecated Use helper methods without object instantiation.
   * All method is this class is static.
   */
  @Deprecated
  public GradleExecutionHelper() { }

  private static final Logger LOG = Logger.getInstance(GradleExecutionHelper.class);

  /**
   * @deprecated Use instead the static variant of this method.
   */
  @Deprecated
  public <T> T execute(
    @NotNull String projectPath,
    @Nullable GradleExecutionSettings settings,
    @NotNull Function<? super ProjectConnection, ? extends T> f
  ) {
    return execute(projectPath, settings, null, null, null, f);
  }

  public static <T> T execute(
    @NotNull String projectPath,
    @Nullable GradleExecutionSettings settings,
    @Nullable ExternalSystemTaskId taskId,
    @Nullable ExternalSystemTaskNotificationListener listener,
    @Nullable CancellationToken cancellationToken,
    @NotNull Function<? super ProjectConnection, ? extends T> f
  ) {
    String projectDir;
    File projectPathFile = new File(projectPath);
    if (projectPathFile.isFile() && projectPath.endsWith(GradleConstants.EXTENSION) && projectPathFile.getParent() != null) {
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
    return withGradleConnection(projectDir, taskId, settings, listener, cancellationToken, connection -> {
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

  @ApiStatus.Internal
  public static void prepareForExecution(
    @NotNull LongRunningOperation operation,
    @NotNull CancellationToken cancellationToken,
    @NotNull ExternalSystemTaskId id,
    @NotNull GradleExecutionSettings settings,
    @NotNull ExternalSystemTaskNotificationListener listener,
    @Nullable BuildEnvironment buildEnvironment
  ) {
    clearSystemProperties(operation);

    applyIdeaParameters(settings);

    setupJvmArguments(operation, settings, buildEnvironment);

    setupLogging(settings, buildEnvironment);

    setupArguments(operation, settings);

    setupEnvironment(operation, settings);

    setupJavaHome(operation, settings, id);

    setupProgressListeners(operation, settings, id, listener, buildEnvironment);

    setupStandardIO(operation, settings, id, listener);

    operation.withCancellationToken(cancellationToken);

    GradleExecutionHelperExtension.EP_NAME.forEachExtensionSafe(proc -> {
      proc.prepareForExecution(id, operation, settings, buildEnvironment);
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
    @NotNull GradleExecutionSettings settings,
    @Nullable BuildEnvironment buildEnvironment
  ) {
    var buildRoot = getBuildRoot(buildEnvironment);
    if (buildRoot != null) {
      var properties = GradlePropertiesFile.getProperties(settings.getServiceDirectory(), buildRoot);
      var jvmArgsProperty = properties.getJvmOptions();
      if (jvmArgsProperty != null) {
        var jvmArgs = ParametersListUtil.parse(jvmArgsProperty.getValue());
        LOG.debug("Passing JVM args from gradle.properties to Gradle Tooling API: " + jvmArgs);
        operation.addJvmArguments(jvmArgs);
      }
    }

    var jvmArgs = ContainerUtil.filter(settings.getJvmArguments(), it -> !StringUtil.isEmpty(it));
    LOG.debug("Passing custom JVM args to Gradle Tooling API: " + jvmArgs);
    operation.addJvmArguments(jvmArgs);
  }

  private static void setupJavaHome(
    @NotNull LongRunningOperation operation,
    @NotNull GradleExecutionSettings settings,
    @NotNull ExternalSystemTaskId id
  ) {
    var javaHome = GradleDaemonJvmHelper.isExecutingUpdateDaemonJvmTask(settings)
                   ? GradleDaemonJvmHelper.getGradleJvmForUpdateDaemonJvmTask(id)
                   : settings.getJavaHome();
    if (javaHome != null && new File(javaHome).isDirectory()) {
      LOG.debug("Java home to set for Gradle operation: " + javaHome);
      operation.setJavaHome(new File(javaHome));
    }
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

  @Nullable
  public static BuildEnvironment getBuildEnvironment(@NotNull ProjectConnection connection,
                                                     @NotNull ExternalSystemTaskId taskId,
                                                     @NotNull ExternalSystemTaskNotificationListener listener,
                                                     @Nullable CancellationToken cancellationToken,
                                                     @Nullable GradleExecutionSettings settings) {
    Span span = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)
      .spanBuilder("GetBuildEnvironment")
      .startSpan();
    try (Scope ignore = span.makeCurrent()) {
      BuildEnvironment buildEnvironment = null;
      try {
        ModelBuilder<BuildEnvironment> modelBuilder = connection.model(BuildEnvironment.class);
        if (cancellationToken != null) {
          modelBuilder.withCancellationToken(cancellationToken);
        }
        if (settings != null) {
          setupJavaHome(modelBuilder, settings, taskId);
        }
        // do not use connection.getModel methods since it doesn't allow to handle progress events
        // and we can miss gradle tooling client side events like distribution download.
        GradleProgressListener gradleProgressListener = new GradleProgressListener(listener, taskId);
        modelBuilder.addProgressListener((ProgressListener)gradleProgressListener);
        modelBuilder.addProgressListener((org.gradle.tooling.events.ProgressListener)gradleProgressListener);
        modelBuilder.setStandardOutput(new OutputWrapper(listener, taskId, true));
        modelBuilder.setStandardError(new OutputWrapper(listener, taskId, false));

        buildEnvironment = modelBuilder.get();
      }
      catch (Throwable t) {
        span.recordException(t);
        span.setStatus(StatusCode.ERROR);
        LOG.warn("Failed to obtain build environment from Gradle daemon.", t);
      }
      if (buildEnvironment != null) {
        checkThatGradleBuildEnvironmentIsSupportedByIdea(buildEnvironment);
      }
      return buildEnvironment;
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

  @NotNull
  @VisibleForTesting
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
