// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.process.internal.JvmOptions;
import org.gradle.tooling.*;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.DistributionFactoryExt;
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.tooling.internal.init.Init;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleEnvironment;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Denis Zhdanov
 * @since 3/14/13 5:11 PM
 */
public class GradleExecutionHelper {

  private static final Logger LOG = Logger.getInstance(GradleExecutionHelper.class);

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  public <T> ModelBuilder<T> getModelBuilder(@NotNull Class<T> modelType,
                                             @NotNull final ExternalSystemTaskId id,
                                             @Nullable GradleExecutionSettings settings,
                                             @NotNull ProjectConnection connection,
                                             @NotNull ExternalSystemTaskNotificationListener listener) {
    ModelBuilder<T> result = connection.model(modelType);
    if (settings != null) {
      prepare(result, id, settings, listener, connection);
    }
    return result;
  }

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  public BuildLauncher getBuildLauncher(@NotNull final ExternalSystemTaskId id,
                                        @NotNull ProjectConnection connection,
                                        @Nullable GradleExecutionSettings settings,
                                        @NotNull ExternalSystemTaskNotificationListener listener) {
    BuildLauncher result = connection.newBuild();
    if (settings != null) {
      prepare(result, id, settings, listener, connection);
    }
    return result;
  }

  @Nullable
  public static BuildEnvironment getBuildEnvironment(ProjectResolverContext projectResolverContext) {
    return getBuildEnvironment(projectResolverContext.getConnection(),
                               projectResolverContext.getExternalSystemTaskId(),
                               projectResolverContext.getListener());
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public static void prepare(@NotNull LongRunningOperation operation,
                             @NotNull final ExternalSystemTaskId id,
                             @NotNull GradleExecutionSettings settings,
                             @NotNull final ExternalSystemTaskNotificationListener listener,
                             @NotNull ProjectConnection connection) {
    prepare(operation, id, settings, listener, connection, new OutputWrapper(listener, id, true), new OutputWrapper(listener, id, false));
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public static void prepare(@NotNull LongRunningOperation operation,
                             @NotNull final ExternalSystemTaskId id,
                             @NotNull GradleExecutionSettings settings,
                             @NotNull final ExternalSystemTaskNotificationListener listener,
                             @NotNull ProjectConnection connection,
                             @NotNull final OutputStream standardOutput,
                             @NotNull final OutputStream standardError) {
    Set<String> jvmArgs = settings.getVmOptions();
    BuildEnvironment buildEnvironment = getBuildEnvironment(connection, id, listener);

    String gradleVersion = buildEnvironment != null ? buildEnvironment.getGradle().getGradleVersion() : null;
    if (!jvmArgs.isEmpty()) {
      // merge gradle args e.g. defined in gradle.properties
      Collection<String> merged = buildEnvironment != null
                                  ? mergeJvmArgs(settings.getServiceDirectory(), buildEnvironment.getJava().getJvmArguments(), jvmArgs)
                                  : jvmArgs;

      // filter nulls and empty strings
      List<String> filteredArgs = ContainerUtil.mapNotNull(merged, s -> StringUtil.isEmpty(s) ? null : s);

      operation.setJvmArguments(ArrayUtil.toStringArray(filteredArgs));
    }

    if (settings.isOfflineWork()) {
      settings.withArgument(GradleConstants.OFFLINE_MODE_CMD_OPTION);
    }

    final Application application = ApplicationManager.getApplication();
    if (application != null && application.isUnitTestMode()) {
      if (!settings.getArguments().contains("--quiet")) {
        settings.withArgument("--info");
      }
      settings.withArgument("--recompile-scripts");
    }

    if (!settings.getArguments().isEmpty()) {
      String loggableArgs = StringUtil.join(obfuscatePasswordParameters(settings.getArguments()), " ");
      LOG.info("Passing command-line args to Gradle Tooling API: " + loggableArgs);

      // filter nulls and empty strings
      List<String> filteredArgs = ContainerUtil.mapNotNull(settings.getArguments(), s -> StringUtil.isEmpty(s) ? null : s);

      // TODO remove this replacement when --tests option will become available for tooling API
      replaceTestCommandOptionWithInitScript(filteredArgs);
      operation.withArguments(ArrayUtil.toStringArray(filteredArgs));
    }
    setupEnvironment(operation, settings, gradleVersion, id, listener);

    final String javaHome = settings.getJavaHome();
    if (javaHome != null && new File(javaHome).isDirectory()) {
      operation.setJavaHome(new File(javaHome));
    }

    String buildRootDir = buildEnvironment == null ? null : buildEnvironment.getBuildIdentifier().getRootDir().getPath();
    GradleProgressListener gradleProgressListener = new GradleProgressListener(listener, id, buildRootDir);
    operation.addProgressListener((ProgressListener)gradleProgressListener);
    operation.addProgressListener((org.gradle.tooling.events.ProgressListener)gradleProgressListener);
    operation.setStandardOutput(standardOutput);
    operation.setStandardError(standardError);
    InputStream inputStream = settings.getUserData(ExternalSystemRunConfiguration.RUN_INPUT_KEY);
    if (inputStream != null) {
      operation.setStandardInput(inputStream);
    }
  }

  private static void setupEnvironment(@NotNull LongRunningOperation operation,
                                       @NotNull GradleExecutionSettings settings,
                                       @Nullable String gradleVersion,
                                       ExternalSystemTaskId taskId,
                                       ExternalSystemTaskNotificationListener listener) {
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
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.withEnvironment(settings.getEnv());
    commandLine.withParentEnvironmentType(
      settings.isPassParentEnvs() ? GeneralCommandLine.ParentEnvironmentType.CONSOLE : GeneralCommandLine.ParentEnvironmentType.NONE);
    Map<String, String> effectiveEnvironment = commandLine.getEffectiveEnvironment();
    operation.setEnvironmentVariables(effectiveEnvironment);
  }

  public <T> T execute(@NotNull String projectPath, @Nullable GradleExecutionSettings settings, @NotNull Function<ProjectConnection, T> f) {

    final String projectDir;
    final File projectPathFile = new File(projectPath);
    if (projectPathFile.isFile() && projectPath.endsWith(GradleConstants.EXTENSION)
        && projectPathFile.getParent() != null) {
      projectDir = projectPathFile.getParent();
    }
    else {
      projectDir = projectPath;
    }

    String userDir = null;
    if (!GradleEnvironment.ADJUST_USER_DIR) {
      try {
        userDir = System.getProperty("user.dir");
        if (userDir != null) System.setProperty("user.dir", projectDir);
      }
      catch (Exception ignore) {
      }
    }
    ProjectConnection connection = getConnection(projectDir, settings);
    try {
      return f.fun(connection);
    }
    catch (ExternalSystemException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.debug("Gradle execution error", e);
      Throwable rootCause = ExceptionUtil.getRootCause(e);
      throw new ExternalSystemException(ExceptionUtil.getMessage(rootCause));
    }
    finally {
      try {
        connection.close();
        if (userDir != null) {
          // restore original user.dir property
          System.setProperty("user.dir", userDir);
        }
      }
      catch (Throwable e) {
        LOG.debug("Gradle connection close error", e);
      }
    }
  }

  public void ensureInstalledWrapper(@NotNull ExternalSystemTaskId id,
                                     @NotNull String projectPath,
                                     @NotNull GradleExecutionSettings settings,
                                     @NotNull ExternalSystemTaskNotificationListener listener) {

    if (!settings.getDistributionType().isWrapped()) return;

    if (settings.getDistributionType() == DistributionType.DEFAULT_WRAPPED &&
        GradleUtil.findDefaultWrapperPropertiesFile(projectPath) != null) {
      return;
    }

    final long ttlInMs = settings.getRemoteProcessIdleTtlInMs();
    ProjectConnection connection = getConnection(projectPath, settings);
    try {
      settings.setRemoteProcessIdleTtlInMs(100);
      try {
        final File wrapperPropertyFileLocation = FileUtil.createTempFile("wrap", "loc");
        wrapperPropertyFileLocation.deleteOnExit();
        final String[] lines = {
          "",
          "gradle.taskGraph.afterTask { Task task ->",
          "    if (task instanceof Wrapper) {",
          "        def wrapperPropertyFileLocation = task.jarFile.getCanonicalPath() - '.jar' + '.properties'",
          "        new File('" +
          StringUtil.escapeBackSlashes(wrapperPropertyFileLocation.getCanonicalPath()) +
          "').write wrapperPropertyFileLocation",
          "}}",
          "",
        };
        final File tempFile = writeToFileGradleInitScript(StringUtil.join(lines, SystemProperties.getLineSeparator()));
        settings.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, tempFile.getAbsolutePath());
        BuildLauncher launcher = getBuildLauncher(id, connection, settings, listener);
        launcher.forTasks("wrapper");
        launcher.run();
        String wrapperPropertyFile = FileUtil.loadFile(wrapperPropertyFileLocation);
        settings.setWrapperPropertyFile(wrapperPropertyFile);
      }
      catch (IOException e) {
        LOG.warn("Can't update wrapper", e);
      }
    }
    catch (Throwable e) {
      LOG.warn("Can't update wrapper", e);
    }
    finally {
      settings.setRemoteProcessIdleTtlInMs(ttlInMs);
      try {
        connection.close();
      }
      catch (Throwable e) {
        // ignore
      }
    }
  }

  private static List<String> mergeJvmArgs(String serviceDirectory, List<String> jvmArgs, Set<String> jvmArgsFromIdeSettings) {
    File gradleUserHomeDir = serviceDirectory != null ? new File(serviceDirectory) : new BuildLayoutParameters().getGradleUserHomeDir();
    LOG.debug("Gradle home: " + gradleUserHomeDir);
    NativeServices.initialize(gradleUserHomeDir);
    Map<String, String> mergedArgs = new LinkedHashMap<>();
    for (String jvmArg : ContainerUtil.concat(jvmArgs, jvmArgsFromIdeSettings)) {
      int i = jvmArg.indexOf('=');
      if(i <= 0) {
        mergedArgs.put(jvmArg, "");
      } else {
        mergedArgs.put(jvmArg.substring(0, i), jvmArg.substring(i));
      }
    }

    List<String> mergedList = new ArrayList<>();
    for (Map.Entry<String, String> entry : mergedArgs.entrySet()) {
      mergedList.add(entry.getKey() + entry.getValue());
    }
    JvmOptions jvmOptions = new JvmOptions(null);
    jvmOptions.setAllJvmArgs(mergedList);
    return jvmOptions.getAllJvmArgs();
  }

  /**
   * Allows to retrieve gradle api connection to use for the given project.
   *
   * @param projectPath target project path
   * @param settings    execution settings to use
   * @return connection to use
   * @throws IllegalStateException if it's not possible to create the connection
   */
  @NotNull
  private static ProjectConnection getConnection(@NotNull String projectPath,
                                                 @Nullable GradleExecutionSettings settings)
    throws IllegalStateException {
    File projectDir = new File(projectPath);
    GradleConnector connector = GradleConnector.newConnector();
    int ttl = -1;

    if (settings != null) {
      File gradleHome = settings.getGradleHome() == null ? null : new File(settings.getGradleHome());
      //noinspection EnumSwitchStatementWhichMissesCases
      switch (settings.getDistributionType()) {
        case LOCAL:
          if (gradleHome != null) {
            connector.useInstallation(gradleHome);
          }
          break;
        case WRAPPED:
          if (settings.getWrapperPropertyFile() != null) {
            DistributionFactoryExt.setWrappedDistribution(connector, settings.getWrapperPropertyFile(), gradleHome);
          }
          break;
      }

      // Setup service directory if necessary.
      String serviceDirectory = settings.getServiceDirectory();
      if (serviceDirectory != null) {
        connector.useGradleUserHomeDir(new File(serviceDirectory));
      }

      // Setup logging if necessary.
      if (settings.isVerboseProcessing() && connector instanceof DefaultGradleConnector) {
        ((DefaultGradleConnector)connector).setVerboseLogging(true);
      }
      ttl = (int)settings.getRemoteProcessIdleTtlInMs();
    }

    // do not spawn gradle daemons during test execution
    final Application app = ApplicationManager.getApplication();
    ttl = (app != null && app.isUnitTestMode()) ? 10000 : ttl;

    if (ttl > 0 && connector instanceof DefaultGradleConnector) {
      ((DefaultGradleConnector)connector).daemonMaxIdleTime(ttl, TimeUnit.MILLISECONDS);
    }
    connector.forProjectDirectory(projectDir);
    ProjectConnection connection = connector.connect();
    if (connection == null) {
      throw new IllegalStateException(String.format(
        "Can't create connection to the target project via gradle tooling api. Project path: '%s'", projectPath
      ));
    }
    return connection;
  }

  @Nullable
  public static File generateInitScript(boolean isBuildSrcProject, @NotNull Set<Class> toolingExtensionClasses) {
    InputStream stream = Init.class.getResourceAsStream("/org/jetbrains/plugins/gradle/tooling/internal/init/init.gradle");
    try {
      if (stream == null) {
        LOG.warn("Can't get init script template");
        return null;
      }
      final String toolingExtensionsJarPaths = getToolingExtensionsJarPaths(toolingExtensionClasses);
      String script = FileUtil.loadTextAndClose(stream).replaceFirst(Pattern.quote("${EXTENSIONS_JARS_PATH}"), toolingExtensionsJarPaths);
      if (isBuildSrcProject) {
        String buildSrcDefaultInitScript = getBuildSrcDefaultInitScript();
        if (buildSrcDefaultInitScript == null) return null;
        script += buildSrcDefaultInitScript;
      }

      return writeToFileGradleInitScript(script);
    }
    catch (Exception e) {
      LOG.warn("Can't generate IJ gradle init script", e);
      return null;
    }
    finally {
      StreamUtil.closeStream(stream);
    }
  }

  public static File writeToFileGradleInitScript(@NotNull String content) throws IOException {
    return writeToFileGradleInitScript(content, "ijinit");
  }

  public static File writeToFileGradleInitScript(@NotNull String content, @NotNull String filePrefix) throws IOException {
    File tempFile = new File(FileUtil.getTempDirectory(), filePrefix + '.' + GradleConstants.EXTENSION);
    if (tempFile.exists() && StringUtil.equals(content, FileUtil.loadFile(tempFile))) {
      return tempFile;
    }
    tempFile = FileUtil.findSequentNonexistentFile(tempFile.getParentFile(), filePrefix, GradleConstants.EXTENSION);
    FileUtil.writeToFile(tempFile, content);
    tempFile.deleteOnExit();
    return tempFile;
  }

  @Nullable
  public static String getBuildSrcDefaultInitScript() {
    InputStream stream = Init.class.getResourceAsStream("/org/jetbrains/plugins/gradle/tooling/internal/init/buildSrcInit.gradle");
    try {
      if (stream == null) return null;
      return FileUtil.loadTextAndClose(stream);
    }
    catch (Exception e) {
      LOG.warn("Can't use IJ gradle init script", e);
      return null;
    }
    finally {
      StreamUtil.closeStream(stream);
    }
  }

  @Nullable
  public static GradleVersion getGradleVersion(@NotNull ProjectConnection connection,
                                               @NotNull ExternalSystemTaskId taskId,
                                               @NotNull ExternalSystemTaskNotificationListener listener) {
    final BuildEnvironment buildEnvironment = getBuildEnvironment(connection, taskId, listener);

    GradleVersion gradleVersion = null;
    if (buildEnvironment != null) {
      gradleVersion = GradleVersion.version(buildEnvironment.getGradle().getGradleVersion());
    }
    return gradleVersion;
  }

  @Nullable
  public static BuildEnvironment getBuildEnvironment(@NotNull ProjectConnection connection,
                                                     @NotNull ExternalSystemTaskId taskId,
                                                     @NotNull ExternalSystemTaskNotificationListener listener) {
    ModelBuilder<BuildEnvironment> modelBuilder = connection.model(BuildEnvironment.class);
    // do not use connection.getModel methods since it doesn't allow to handle progress events
    // and we can miss gradle tooling client side events like distribution download.
    GradleProgressListener gradleProgressListener = new GradleProgressListener(listener, taskId);
    modelBuilder.addProgressListener((ProgressListener)gradleProgressListener);
    modelBuilder.addProgressListener((org.gradle.tooling.events.ProgressListener)gradleProgressListener);
    modelBuilder.setStandardOutput(new OutputWrapper(listener, taskId, true));
    modelBuilder.setStandardError(new OutputWrapper(listener, taskId, false));

    final BuildEnvironment buildEnvironment = modelBuilder.get();
    if (LOG.isDebugEnabled()) {
      try {
        LOG.debug("Gradle version: " + buildEnvironment.getGradle().getGradleVersion());
        LOG.debug("Gradle java home: " + buildEnvironment.getJava().getJavaHome());
        LOG.debug("Gradle jvm arguments: " + buildEnvironment.getJava().getJvmArguments());
      }
      catch (Throwable t) {
        LOG.debug(t);
      }
    }
    return buildEnvironment;
  }

  private static void replaceTestCommandOptionWithInitScript(@NotNull List<String> args) {
    Set<String> testIncludePatterns = ContainerUtil.newLinkedHashSet();
    Iterator<String> it = args.iterator();
    while (it.hasNext()) {
      final String next = it.next();
      if ("--tests".equals(next)) {
        it.remove();
        if (it.hasNext()) {
          testIncludePatterns.add(it.next());
          it.remove();
        }
      }
    }
    if (!testIncludePatterns.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      buf.append('[');
      for (Iterator<String> iterator = testIncludePatterns.iterator(); iterator.hasNext(); ) {
        String pattern = iterator.next();
        buf.append('\'').append(pattern).append('\'');
        if (iterator.hasNext()) {
          buf.append(',');
        }
      }
      buf.append(']');

      String path = renderInitScript(buf.toString());
      if (path != null) {
        ContainerUtil.addAll(args, GradleConstants.INIT_SCRIPT_CMD_OPTION, path);
      }
    }
  }

  @Nullable
  public static String renderInitScript(@NotNull String testArgs) {
    InputStream stream = Init.class.getResourceAsStream("/org/jetbrains/plugins/gradle/tooling/internal/init/testFilterInit.gradle");
    try {
      if (stream == null) {
        LOG.error("Can't get test filter init script template");
        return null;
      }
      String script = FileUtil.loadTextAndClose(stream).replaceFirst(Pattern.quote("${TEST_NAME_INCLUDES}"), Matcher.quoteReplacement(testArgs));
      final File tempFile = writeToFileGradleInitScript(script, "ijtestinit");
      return tempFile.getAbsolutePath();
    }
    catch (Exception e) {
      LOG.warn("Can't generate IJ gradle test filter init script", e);
      return null;
    }
    finally {
      StreamUtil.closeStream(stream);
    }
  }

  @NotNull
  private static String getToolingExtensionsJarPaths(@NotNull Set<Class> toolingExtensionClasses) {
    final Set<String> jarPaths = ContainerUtil.map2SetNotNull(toolingExtensionClasses, aClass -> {
      String path = PathManager.getJarPathForClass(aClass);
      return path == null ? null : PathUtil.getCanonicalPath(path);
    });
    StringBuilder buf = new StringBuilder();
    buf.append('[');
    for (Iterator<String> it = jarPaths.iterator(); it.hasNext(); ) {
      String jarPath = it.next();
      buf.append('\"').append(jarPath).append('\"');
      if (it.hasNext()) {
        buf.append(',');
      }
    }
    buf.append(']');
    return buf.toString();
  }

  /* deprecated methods to be removed in future version */

  /**
   * @deprecated {@link #getModelBuilder(Class, ExternalSystemTaskId, GradleExecutionSettings, ProjectConnection, ExternalSystemTaskNotificationListener)}
   */
  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  public <T> ModelBuilder<T> getModelBuilder(@NotNull Class<T> modelType,
                                             @NotNull final ExternalSystemTaskId id,
                                             @Nullable GradleExecutionSettings settings,
                                             @NotNull ProjectConnection connection,
                                             @NotNull ExternalSystemTaskNotificationListener listener,
                                             @NotNull List<String> extraJvmArgs) {
    ModelBuilder<T> result = connection.model(modelType);
    prepare(result, id, settings, listener, extraJvmArgs, ContainerUtil.newArrayList(), connection);
    return result;
  }


  /**
   * @deprecated {@link #getBuildLauncher(ExternalSystemTaskId, ProjectConnection, GradleExecutionSettings, ExternalSystemTaskNotificationListener)}
   */
  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  public BuildLauncher getBuildLauncher(@NotNull final ExternalSystemTaskId id,
                                        @NotNull ProjectConnection connection,
                                        @Nullable GradleExecutionSettings settings,
                                        @NotNull ExternalSystemTaskNotificationListener listener,
                                        @NotNull final List<String> vmOptions,
                                        @NotNull final List<String> commandLineArgs) {
    BuildLauncher result = connection.newBuild();
    prepare(result, id, settings, listener, vmOptions, commandLineArgs, connection);
    return result;
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

  /**
   * @deprecated to be removed in future version
   */
  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public static void prepare(@NotNull LongRunningOperation operation,
                             @NotNull final ExternalSystemTaskId id,
                             @Nullable GradleExecutionSettings settings,
                             @NotNull final ExternalSystemTaskNotificationListener listener,
                             @NotNull List<String> extraJvmArgs,
                             @NotNull ProjectConnection connection) {
    if (settings == null) return;
    settings.withVmOptions(extraJvmArgs);
    prepare(operation, id, settings, listener, connection, new OutputWrapper(listener, id, true), new OutputWrapper(listener, id, false));
  }

  /**
   * @deprecated use {@link #prepare(LongRunningOperation, ExternalSystemTaskId, GradleExecutionSettings, ExternalSystemTaskNotificationListener, ProjectConnection)}
   */
  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public static void prepare(@NotNull LongRunningOperation operation,
                             @NotNull final ExternalSystemTaskId id,
                             @Nullable GradleExecutionSettings settings,
                             @NotNull final ExternalSystemTaskNotificationListener listener,
                             @NotNull List<String> extraJvmArgs,
                             @NotNull List<String> commandLineArgs,
                             @NotNull ProjectConnection connection) {
    if (settings == null) return;
    settings.withArguments(commandLineArgs).withVmOptions(extraJvmArgs);
    prepare(operation, id, settings, listener, connection, new OutputWrapper(listener, id, true), new OutputWrapper(listener, id, false));
  }

  /**
   * @deprecated use {@link #prepare(LongRunningOperation, ExternalSystemTaskId, GradleExecutionSettings, ExternalSystemTaskNotificationListener, ProjectConnection, OutputStream, OutputStream)}
   */
  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public static void prepare(@NotNull LongRunningOperation operation,
                             @NotNull final ExternalSystemTaskId id,
                             @NotNull GradleExecutionSettings settings,
                             @NotNull final ExternalSystemTaskNotificationListener listener,
                             @NotNull List<String> extraJvmArgs,
                             @NotNull List<String> commandLineArgs,
                             @NotNull ProjectConnection connection,
                             @NotNull final OutputStream standardOutput,
                             @NotNull final OutputStream standardError) {
    settings.withArguments(commandLineArgs).withVmOptions(extraJvmArgs);
    prepare(operation, id, settings, listener, connection, standardOutput, standardError);
  }
}
