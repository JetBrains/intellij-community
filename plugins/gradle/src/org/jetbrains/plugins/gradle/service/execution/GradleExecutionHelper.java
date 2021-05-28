// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
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
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.apache.commons.cli.Option;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.process.internal.JvmOptions;
import org.gradle.tooling.*;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider;
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.tooling.internal.init.Init;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleEnvironment;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jetbrains.plugins.gradle.GradleConnectorService.withGradleConnection;

/**
 * @author Denis Zhdanov
 */
public class GradleExecutionHelper {

  private static final Logger LOG = Logger.getInstance(GradleExecutionHelper.class);

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
        String userDir = null;
        if (!Registry.is("gradle.tooling.use.external.process", false) && !GradleEnvironment.ADJUST_USER_DIR) {
          try {
            userDir = System.getProperty("user.dir");
            if (userDir != null) System.setProperty("user.dir", projectDir);
          }
          catch (Exception ignore) {
          }
        }

        try {
          return f.fun(connection);
        }
        catch (ExternalSystemException e) {
          throw e;
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.warn("Gradle execution error", e);
          Throwable rootCause = ExceptionUtil.getRootCause(e);
          ExternalSystemException externalSystemException =
            new ExternalSystemException(ExceptionUtil.getMessage(rootCause), e);
          externalSystemException.initCause(e);
          throw externalSystemException;
        }
        finally {
          if (userDir != null) {
            // restore original user.dir property
            System.setProperty("user.dir", userDir);
          }
        }
      });
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

    if (!settings.getDistributionType().isWrapped()) return;

    if (settings.getDistributionType() == DistributionType.DEFAULT_WRAPPED &&
        GradleUtil.findDefaultWrapperPropertiesFile(projectPath) != null) {
      return;
    }

    withGradleConnection(
      projectPath, id, settings, listener, cancellationToken,
      connection -> {
        long ttlInMs = settings.getRemoteProcessIdleTtlInMs();
        try {
          settings.setRemoteProcessIdleTtlInMs(100);
          TargetEnvironmentConfigurationProvider configurationProvider =
            ExternalSystemExecutionAware.Companion.getEnvironmentConfigurationProvider(settings);
          if (configurationProvider != null) {
            // todo add the support for org.jetbrains.plugins.gradle.settings.DistributionType.WRAPPED
            BuildLauncher launcher = getBuildLauncher(id, connection, settings, listener);
            launcher.withCancellationToken(cancellationToken);
            launcher.forTasks("wrapper");
            launcher.run();

            File wrapperPropertiesFile = GradleUtil.findDefaultWrapperPropertiesFile(projectPath);
            if (wrapperPropertiesFile != null) {
              settings.setWrapperPropertyFile(wrapperPropertiesFile.getPath());
            }
            return null;
          }
          try {
            final File wrapperFilesLocation = FileUtil.createTempDirectory("wrap", "loc");
            final String fileName = "gradle-wrapper";
            final File jarFile = new File(wrapperFilesLocation, fileName + ".jar");
            final File scriptFile = new File(wrapperFilesLocation, "gradlew");
            final File pathToProperties = new File(wrapperFilesLocation, "path.tmp");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> FileUtil.delete(wrapperFilesLocation), "GradleExecutionHelper cleanup"));

            StringJoiner lines = new StringJoiner(System.lineSeparator());
            lines.add("");
            lines.add("gradle.projectsEvaluated { gr ->");
            lines.add("  def wrapper = gr.rootProject.tasks[\"wrapper\"]");
            lines.add("  if (wrapper != null) {");
            lines.add("    if (wrapper.jarFile.exists()) {");
            lines.add("      wrapper.jarFile = new File('" + StringUtil.escapeBackSlashes(jarFile.getCanonicalPath()) + "')");
            lines.add("      wrapper.scriptFile = new File('" + StringUtil.escapeBackSlashes(scriptFile.getCanonicalPath()) + "')");
            lines.add("    }");
            if (gradleVersion != null) {
              lines.add("    wrapper.gradleVersion = '" + gradleVersion.getVersion() + "'");
            }
            lines.add("    wrapper.doLast {");
            lines.add("      new File('" +
                      StringUtil.escapeBackSlashes(pathToProperties.getCanonicalPath()) +
                      "').write wrapper.propertiesFile.getCanonicalPath()");
            lines.add("    }");
            lines.add("  }");
            lines.add("}");
            lines.add("");
            final File tempFile = writeToFileGradleInitScript(lines.toString(), "wrapper_init");
            settings.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, tempFile.getAbsolutePath());
            BuildLauncher launcher = getBuildLauncher(id, connection, settings, listener);
            launcher.withCancellationToken(cancellationToken);
            launcher.forTasks("wrapper");
            launcher.run();
            settings.setWrapperPropertyFile(FileUtil.loadFile(pathToProperties));
          }
          catch (IOException e) {
            LOG.warn("Can't update wrapper", e);
          }
        }
        catch (ProcessCanceledException e) {
          throw e;
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
        }
        return null;
      }
    );
  }

  @NotNull
  public TestLauncher getTestLauncher(@NotNull final ExternalSystemTaskId id,
                                      @NotNull ProjectConnection connection,
                                      @NotNull List<String> tasks,
                                      @Nullable GradleExecutionSettings settings,
                                      @NotNull ExternalSystemTaskNotificationListener listener) {
    TestLauncher result = connection.newTestLauncher();
    if (settings != null) {
      prepareForTestLauncher(result, id, settings, listener, connection, tasks);
    }
    return result;
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

  public static void prepare(@NotNull LongRunningOperation operation,
                             @NotNull final ExternalSystemTaskId id,
                             @NotNull GradleExecutionSettings settings,
                             @NotNull final ExternalSystemTaskNotificationListener listener,
                             @NotNull ProjectConnection connection) {
    prepare(operation, id, settings, listener, connection,
            new OutputWrapper(listener, id, true), new OutputWrapper(listener, id, false),
            false, Collections.emptyList());
  }

  public static void prepareForTestLauncher(@NotNull LongRunningOperation operation,
                                            @NotNull final ExternalSystemTaskId id,
                                            @NotNull GradleExecutionSettings settings,
                                            @NotNull final ExternalSystemTaskNotificationListener listener,
                                            @NotNull ProjectConnection connection,
                                            @NotNull List<String> tasks) {
    prepare(operation, id, settings, listener, connection,
            new OutputWrapper(listener, id, true), new OutputWrapper(listener, id, false),
            true, tasks);
  }

  public static void prepare(@NotNull LongRunningOperation operation,
                             @NotNull final ExternalSystemTaskId id,
                             @NotNull GradleExecutionSettings settings,
                             @NotNull final ExternalSystemTaskNotificationListener listener,
                             @NotNull ProjectConnection connection,
                             @NotNull final OutputStream standardOutput,
                             @NotNull final OutputStream standardError,
                             boolean forTestLauncher,
                             @NotNull List<String> tasks) {
    List<String> jvmArgs = settings.getJvmArguments();
    BuildEnvironment buildEnvironment = getBuildEnvironment(connection, id, listener, (CancellationToken)null, settings);

    String gradleVersion = buildEnvironment != null ? buildEnvironment.getGradle().getGradleVersion() : null;
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
        merged = mergeJvmArgs(settings.getServiceDirectory(), buildJvmArguments, jvmArgs);
      }
      else {
        merged = jvmArgs;
      }

      // filter nulls and empty strings
      List<String> filteredArgs = ContainerUtil.mapNotNull(merged, s -> StringUtil.isEmpty(s) ? null : s);

      operation.setJvmArguments(ArrayUtilRt.toStringArray(filteredArgs));
    }

    if (settings.isOfflineWork()) {
      settings.withArgument(GradleConstants.OFFLINE_MODE_CMD_OPTION);
    }

    final Application application = ApplicationManager.getApplication();
    if (application != null && application.isUnitTestMode()) {
      if (!settings.getArguments().contains("--quiet")) {
        if (!settings.getArguments().contains("--debug")) {
          settings.withArgument("--info");
        }
      }
    }

    List<String> filteredArgs = new ArrayList<>();
    if (!settings.getArguments().isEmpty()) {
      String loggableArgs = StringUtil.join(obfuscatePasswordParameters(settings.getArguments()), " ");
      LOG.info("Passing command-line args to Gradle Tooling API: " + loggableArgs);

      // filter nulls and empty strings
      filteredArgs.addAll(ContainerUtil.mapNotNull(settings.getArguments(), s -> StringUtil.isEmpty(s) ? null : s));

      if (forTestLauncher) {
        List<String> tasksWithArgs = new ArrayList<>(tasks);
        tasksWithArgs.addAll(filteredArgs);
        filteredArgs = tasksWithArgs;
        MultiMap<String, String> testTasksConfiguration = extractTestCommandOptions(tasksWithArgs);
        if (operation instanceof TestLauncher) {
          TestLauncher testLauncher = (TestLauncher)operation;
          for (Map.Entry<String, Collection<String>> entry : testTasksConfiguration.entrySet()) {
            // TODO we need better point of call to pass this info
            testLauncher.withTaskAndTestClasses(entry.getKey(), entry.getValue());
          }
        }
      }
      else {
        // TODO remove this replacement when --tests option will become available for tooling API
        replaceTestCommandOptionWithInitScript(filteredArgs);
      }
    }
    filteredArgs.add("-Didea.active=true");
    filteredArgs.add("-Didea.version=" + getIdeaVersion());
    operation.withArguments(ArrayUtilRt.toStringArray(filteredArgs));

    setupEnvironment(operation, settings, gradleVersion, id, listener);

    final String javaHome = settings.getJavaHome();
    if (javaHome != null && new File(javaHome).isDirectory()) {
      LOG.debug("Java home to set for Gradle operation: " + javaHome);
      operation.setJavaHome(new File(javaHome));
    }

    String buildRootDir;
    if (buildEnvironment == null) {
      buildRootDir = null;
    }
    else {
      BuildIdentifier buildIdentifier = getBuildIdentifier(buildEnvironment);
      buildRootDir = buildIdentifier == null ? null : buildIdentifier.getRootDir().getPath();
    }
    GradleProgressListener gradleProgressListener = new GradleProgressListener(listener, id, buildRootDir);
    operation.addProgressListener((ProgressListener)gradleProgressListener);
    if (forTestLauncher) {
      operation.addProgressListener(gradleProgressListener,
                                    OperationType.TASK,
                                    OperationType.TEST,
                                    OperationType.TEST_OUTPUT);
    } else {
      operation.addProgressListener(gradleProgressListener,
                                    OperationType.TASK);
    }
    operation.setStandardOutput(standardOutput);
    operation.setStandardError(standardError);
    InputStream inputStream = settings.getUserData(ExternalSystemRunConfiguration.RUN_INPUT_KEY);
    if (inputStream != null) {
      operation.setStandardInput(inputStream);
    }
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

  @ApiStatus.Experimental
  static List<String> mergeJvmArgs(String serviceDirectory, List<String> jvmArgs, List<String> jvmArgsFromIdeSettings) {
    File gradleUserHomeDir = serviceDirectory != null ? new File(serviceDirectory) : new BuildLayoutParameters().getGradleUserHomeDir();
    LOG.debug("Gradle home: " + gradleUserHomeDir);
    JvmOptions jvmOptions = new JvmOptions(null);
    List<String> mergedJvmArgs = mergeJvmArgs(jvmArgs, jvmArgsFromIdeSettings);
    jvmOptions.setAllJvmArgs(mergedJvmArgs);
    return jvmOptions.getAllJvmArgs();
  }

  @ApiStatus.Experimental
  static List<String> mergeJvmArgs(List<String> jvmArgs, List<String> jvmArgsFromIdeSettings) {
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

  @Nullable
  public static File generateInitScript(boolean isBuildSrcProject, @NotNull Set<Class<?>> toolingExtensionClasses) {
    InputStream stream = Init.class.getResourceAsStream("/org/jetbrains/plugins/gradle/tooling/internal/init/init.gradle");
    if (stream == null) {
      LOG.warn("Can't find init script template");
      return null;
    }
    try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      String toolingExtensionsJarPaths = getToolingExtensionsJarPaths(toolingExtensionClasses);
      String script = StreamUtil.readText(reader).replaceFirst(Pattern.quote("${EXTENSIONS_JARS_PATH}"), toolingExtensionsJarPaths);
      if (isBuildSrcProject) {
        String buildSrcDefaultInitScript = getBuildSrcDefaultInitScript();
        if (buildSrcDefaultInitScript == null) return null;
        script += buildSrcDefaultInitScript;
      }

      return writeToFileGradleInitScript(script, "ijinit");
    }
    catch (Exception e) {
      LOG.warn("Can't generate IJ gradle init script", e);
      return null;
    }
  }

  public static File writeToFileGradleInitScript(@NotNull String content, @NotNull String filePrefix) throws IOException {
    byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
    int contentLength = contentBytes.length;
    return FileUtil.findSequentFile(new File(FileUtil.getTempDirectory()), filePrefix, GradleConstants.EXTENSION, file -> {
      try {
        if (!file.exists()) {
          FileUtil.writeToFile(file, contentBytes, false);
          file.deleteOnExit();
          return true;
        }
        if (contentLength != file.length()) return false;
        return content.equals(FileUtil.loadFile(file, StandardCharsets.UTF_8));
      }
      catch (IOException ignore) {
        // Skip file with access issues. Will attempt to check the next file
      }
      return false;
    });
  }

  @ApiStatus.Internal
  public static void attachTargetPathMapperInitScript(@NotNull GradleExecutionSettings executionSettings) {
    try {
      File initScriptFile = writeToFileGradleInitScript(
        "if(!ext.has('mapPath')) ext.mapPath = { path -> path }\n", "ijmapper");
      executionSettings.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, initScriptFile.getAbsolutePath());
    }
    catch (IOException e) {
      LOG.warn("Can't generate IJ gradle init script", e);
    }
  }

  @Nullable
  public static String getBuildSrcDefaultInitScript() {
    InputStream stream = Init.class.getResourceAsStream("/org/jetbrains/plugins/gradle/tooling/internal/init/buildSrcInit.gradle");
    if (stream == null) {
      LOG.warn("Can't find default init script template");
      return null;
    }
    try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      return StreamUtil.readText(reader);
    }
    catch (Exception e) {
      LOG.warn("Can't use IJ gradle init script", e);
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
          LOG.debug("Gradle version: " + buildEnvironment.getGradle().getGradleVersion());
          LOG.debug("Gradle java home: " + buildEnvironment.getJava().getJavaHome());
          LOG.debug("Gradle jvm arguments: " + buildEnvironment.getJava().getJvmArguments());
        }
        catch (Throwable t) {
          LOG.debug(t);
        }
      }
    }
    catch (Throwable t) {
      LOG.debug(t);
    }
    return buildEnvironment;
  }

  private static void replaceTestCommandOptionWithInitScript(@NotNull List<String> args) {
    Set<String> testIncludePatterns = new LinkedHashSet<>();
    Iterator<String> it = args.iterator();
    while (it.hasNext()) {
      final String next = it.next();
      if (GradleConstants.TESTS_ARG_NAME.equals(next)) {
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
        String groovyPattern = toGroovyString(pattern);
        buf.append('\'').append(groovyPattern).append('\'');
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

  /**
   * For Test Launcher, all tasks are considered test task names and should be set through separate API
   * Args list will contain all other known arguments.
   *
   * @param args full command line
   * @return test tasks grouped with its patterns
   */
  static  MultiMap<String, String> extractTestCommandOptions(@NotNull List<String> args) {
    MultiMap<String, String> taskToTestsPatterns = new MultiMap<>();

    Map<String, Option> optionsIndex = new HashMap<>();
    for (Option option : GradleCommandLineOptionsProvider.getSupportedOptions().getOptions()) {
      optionsIndex.put(option.getOpt(), option);
      optionsIndex.put(option.getLongOpt(), option);
    }

    int j = 0;
    while (j < args.size()) {
      String token = args.get(j);
      Option option = optionsIndex.get(StringUtil.trimLeading(token, '-'));
      if (option!= null) {
        j++;
        if (option.hasArg()) {
          j++;
        }
        continue;
      }

      while (j < args.size() - 2) {
        String peek = args.get(j + 1);
        if (GradleConstants.TESTS_ARG_NAME.equals(peek)) {
          taskToTestsPatterns.putValue(token, args.get(j + 2));
          // remove --tests <pattern> argument
          args.remove(j + 1);
          args.remove(j + 1);
        } else {
          break;
        }
      }

      if (!taskToTestsPatterns.containsKey(token)) {
        taskToTestsPatterns.putValue(token, "*");
      }
      // remove current task name
      args.remove(j);
    }

    return taskToTestsPatterns;
  }

  @NotNull
  public static String toGroovyString(@NotNull String string) {
    StringBuilder stringBuilder = new StringBuilder();
    for (char ch : string.toCharArray()) {
      if (ch == '\\') {
        stringBuilder.append("\\\\");
      }
      else if (ch == '\'') {
        stringBuilder.append("\\'");
      }
      else if (ch == '"') {
        stringBuilder.append("\\\"");
      }
      else if (ch == '$') {
        stringBuilder.append("\\$");
      }
      else {
        stringBuilder.append(ch);
      }
    }
    return stringBuilder.toString();
  }

  @Nullable
  public static String renderInitScript(@NotNull String testArgs) {
    InputStream stream = Init.class.getResourceAsStream("/org/jetbrains/plugins/gradle/tooling/internal/init/testFilterInit.gradle");
    if (stream == null) {
      LOG.error("Can't find test filter init script template");
      return null;
    }
    try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      String script = StreamUtil.readText(reader).replaceFirst(Pattern.quote("${TEST_NAME_INCLUDES}"), Matcher.quoteReplacement(testArgs));
      File tempFile = writeToFileGradleInitScript(script, "ijtestinit");
      return tempFile.getAbsolutePath();
    }
    catch (Exception e) {
      LOG.warn("Can't generate IJ gradle test filter init script", e);
      return null;
    }
  }

  @NotNull
  public static String getToolingExtensionsJarPaths(@NotNull Set<Class<?>> toolingExtensionClasses) {
    final Set<String> jarPaths = ContainerUtil.map2SetNotNull(toolingExtensionClasses, aClass -> {
      String path = PathManager.getJarPathForClass(aClass);
      if (path != null) {
        if (FileUtilRt.getNameWithoutExtension(path).equals("gradle-api-" + GradleVersion.current().getBaseVersion())) {
          LOG.warn("The gradle api jar shouldn't be added to the gradle daemon classpath: {" + aClass + "," + path + "}");
          return null;
        }
        return FileUtil.toCanonicalPath(path);
      }
      return null;
    });
    StringBuilder buf = new StringBuilder();
    buf.append('[');
    for (Iterator<String> it = jarPaths.iterator(); it.hasNext(); ) {
      String jarPath = it.next();
      buf.append("mapPath(\"").append(jarPath).append("\")");
      if (it.hasNext()) {
        buf.append(',');
      }
    }
    buf.append(']');
    return buf.toString();
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
