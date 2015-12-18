/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.execution.configurations.CommandLineTokenizer;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.process.internal.JvmOptions;
import org.gradle.tooling.*;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.consumer.DefaultExecutorServiceFactory;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.DistributionFactoryExt;
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
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
                                             @NotNull ExternalSystemTaskNotificationListener listener,
                                             @NotNull List<String> extraJvmArgs) {
    ModelBuilder<T> result = connection.model(modelType);
    prepare(result, id, settings, listener, extraJvmArgs, ContainerUtil.<String>newArrayList(), connection);
    return result;
  }

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

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public static void prepare(@NotNull LongRunningOperation operation,
                             @NotNull final ExternalSystemTaskId id,
                             @Nullable GradleExecutionSettings settings,
                             @NotNull final ExternalSystemTaskNotificationListener listener,
                             @NotNull List<String> extraJvmArgs,
                             @NotNull List<String> commandLineArgs,
                             @NotNull ProjectConnection connection) {
    prepare(operation, id, settings, listener, extraJvmArgs, commandLineArgs, connection,
            new OutputWrapper(listener, id, true), new OutputWrapper(listener, id, false));
  }


  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public static void prepare(@NotNull LongRunningOperation operation,
                             @NotNull final ExternalSystemTaskId id,
                             @Nullable GradleExecutionSettings settings,
                             @NotNull final ExternalSystemTaskNotificationListener listener,
                             @NotNull List<String> extraJvmArgs,
                             @NotNull List<String> commandLineArgs,
                             @NotNull ProjectConnection connection,
                             @NotNull final OutputStream standardOutput,
                             @NotNull final OutputStream standardError) {
    if (settings == null) {
      return;
    }

    Set<String> jvmArgs = ContainerUtilRt.newHashSet();

    String vmOptions = settings.getDaemonVmOptions();
    if (!StringUtil.isEmpty(vmOptions)) {
      CommandLineTokenizer tokenizer = new CommandLineTokenizer(vmOptions);
      while (tokenizer.hasMoreTokens()) {
        String vmOption = tokenizer.nextToken();
        if (!StringUtil.isEmpty(vmOption)) {
          jvmArgs.add(vmOption);
        }
      }
    }

    jvmArgs.addAll(extraJvmArgs);

    if (!jvmArgs.isEmpty()) {
      // merge gradle args e.g. defined in gradle.properties
      BuildEnvironment buildEnvironment = getBuildEnvironment(connection);
      Collection<String> merged = buildEnvironment != null
                                  ? mergeJvmArgs(settings.getServiceDirectory(), buildEnvironment.getJava().getJvmArguments(), jvmArgs)
                                  : jvmArgs;

      // filter nulls and empty strings
      List<String> filteredArgs = ContainerUtil.mapNotNull(merged, new Function<String, String>() {
        @Override
        public String fun(String s) {
          return StringUtil.isEmpty(s) ? null : s;
        }
      });

      operation.setJvmArguments(ArrayUtil.toStringArray(filteredArgs));
    }

    if (settings.isOfflineWork()) {
      commandLineArgs.add(GradleConstants.OFFLINE_MODE_CMD_OPTION);
    }

    final Application application = ApplicationManager.getApplication();
    if (application != null && application.isUnitTestMode()) {
      commandLineArgs.add("--info");
      commandLineArgs.add("--recompile-scripts");
    }

    if (!commandLineArgs.isEmpty()) {
      LOG.info("Passing command-line args to Gradle Tooling API: " + commandLineArgs);
      // filter nulls and empty strings
      List<String> filteredArgs = ContainerUtil.mapNotNull(commandLineArgs, new Function<String, String>() {
        @Override
        public String fun(String s) {
          return StringUtil.isEmpty(s) ? null : s;
        }
      });

      // TODO remove this replacement when --tests option will become available for tooling API
      replaceTestCommandOptionWithInitScript(filteredArgs);
      operation.withArguments(ArrayUtil.toStringArray(filteredArgs));
    }

    listener.onStart(id);
    final String javaHome = settings.getJavaHome();
    if (javaHome != null && new File(javaHome).isDirectory()) {
      operation.setJavaHome(new File(javaHome));
    }
    operation.addProgressListener(new ProgressListener() {
      @Override
      public void statusChanged(ProgressEvent event) {
        listener.onStatusChange(new ExternalSystemTaskNotificationEvent(id, event.getDescription()));
      }
    });
    operation.addProgressListener(new org.gradle.tooling.events.ProgressListener() {
      @Override
      public void statusChanged(org.gradle.tooling.events.ProgressEvent event) {
        listener.onStatusChange(GradleProgressEventConverter.convert(id, event));
      }
    });
    operation.setStandardOutput(standardOutput);
    operation.setStandardError(standardError);
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

    ProjectConnection connection = getConnection(projectPath, settings);
    try {
      try {
        final File wrapperPropertyFileLocation = FileUtil.createTempFile("wrap", "loc");
        wrapperPropertyFileLocation.deleteOnExit();
        final String[] lines = {
          "gradle.taskGraph.afterTask { Task task ->",
          "    if (task instanceof Wrapper) {",
          "        def wrapperPropertyFileLocation = task.jarFile.getCanonicalPath() - '.jar' + '.properties'",
          "        new File('" +
          StringUtil.escapeBackSlashes(wrapperPropertyFileLocation.getCanonicalPath()) +
          "').write wrapperPropertyFileLocation",
          "}}",
        };
        final File tempFile = writeToFileGradleInitScript(StringUtil.join(lines, SystemProperties.getLineSeparator()));

        BuildLauncher launcher = getBuildLauncher(
          id, connection, settings, listener, ContainerUtil.<String>newArrayList(),
          ContainerUtil.newArrayList(GradleConstants.INIT_SCRIPT_CMD_OPTION, tempFile.getAbsolutePath()));
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
      try {
        connection.close();
      }
      catch (Throwable e) {
        // ignore
      }
    }
  }

  private static List<String> mergeJvmArgs(String serviceDirectory, Iterable<String> jvmArgs1, Iterable<String> jvmArgs2) {
    File gradleUserHomeDir = serviceDirectory != null ? new File(serviceDirectory) : new BuildLayoutParameters().getGradleUserHomeDir();
    LOG.debug("Gradle home: " + gradleUserHomeDir);
    NativeServices.initialize(gradleUserHomeDir, false);
    JvmOptions jvmOptions = new JvmOptions(null);
    jvmOptions.setAllJvmArgs(ContainerUtil.concat(jvmArgs1, jvmArgs2));
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
      //noinspection EnumSwitchStatementWhichMissesCases
      switch (settings.getDistributionType()) {
        case LOCAL:
          String gradleHome = settings.getGradleHome();
          if (gradleHome != null) {
            try {
              // There were problems with symbolic links processing at the gradle side.
              connector.useInstallation(new File(gradleHome).getCanonicalFile());
            }
            catch (IOException e) {
              connector.useInstallation(new File(settings.getGradleHome()));
            }
          }
          break;
        case WRAPPED:
          if (settings.getWrapperPropertyFile() != null) {
            File propertiesFile = new File(settings.getWrapperPropertyFile());
            if (propertiesFile.exists()) {
              Distribution distribution =
                new DistributionFactoryExt(new DefaultExecutorServiceFactory()).getWrappedDistribution(propertiesFile);
              try {
                setField(connector, "distribution", distribution);
              }
              catch (Exception e) {
                throw new ExternalSystemException(e);
              }
            }
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

    if (ttl > 0 && connector instanceof DefaultGradleConnector) {

      // do not spawn gradle daemons during test execution
      final Application app = ApplicationManager.getApplication();
      ttl = (app != null && app.isUnitTestMode()) ? 10000 : ttl;

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

  /**
   * Utility to set field in object if there is no public setter for it.
   * It's not recommended to use this method.
   * FIXME: remove this workaround after gradle API changed
   *
   * @param obj        Object to be modified
   * @param fieldName  name of object's field
   * @param fieldValue value to be set for field
   * @throws SecurityException
   * @throws NoSuchFieldException
   * @throws IllegalArgumentException
   * @throws IllegalAccessException
   */
  public static void setField(Object obj, String fieldName, Object fieldValue)
    throws SecurityException, NoSuchFieldException,
           IllegalArgumentException, IllegalAccessException {
    final Field field = obj.getClass().getDeclaredField(fieldName);
    final boolean isAccessible = field.isAccessible();
    field.setAccessible(true);
    field.set(obj, fieldValue);
    field.setAccessible(isAccessible);
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
  public static GradleVersion getGradleVersion(@NotNull ProjectConnection connection) {
    final BuildEnvironment buildEnvironment = getBuildEnvironment(connection);

    GradleVersion gradleVersion = null;
    if (buildEnvironment != null) {
      gradleVersion = GradleVersion.version(buildEnvironment.getGradle().getGradleVersion());
    }
    return gradleVersion;
  }

  @Nullable
  public static BuildEnvironment getBuildEnvironment(@NotNull ProjectConnection connection) {
    try {
      final BuildEnvironment buildEnvironment = connection.getModel(BuildEnvironment.class);
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
    catch (Exception e) {
      LOG.warn("can not get BuildEnvironment model", e);
      return null;
    }
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
        buf.append('\"').append(pattern).append('\"');
        if (iterator.hasNext()) {
          buf.append(',');
        }
      }
      buf.append(']');

      InputStream stream = Init.class.getResourceAsStream("/org/jetbrains/plugins/gradle/tooling/internal/init/testFilterInit.gradle");
      try {
        if (stream == null) {
          LOG.warn("Can't get test filter init script template");
          return;
        }
        String script = FileUtil.loadTextAndClose(stream).replaceFirst(Pattern.quote("${TEST_NAME_INCLUDES}"), buf.toString());
        final File tempFile = writeToFileGradleInitScript(script, "ijtestinit");
        ContainerUtil.addAll(args, GradleConstants.INIT_SCRIPT_CMD_OPTION, tempFile.getAbsolutePath());
      }
      catch (Exception e) {
        LOG.warn("Can't generate IJ gradle test filter init script", e);
      }
      finally {
        StreamUtil.closeStream(stream);
      }
    }
  }

  @NotNull
  private static String getToolingExtensionsJarPaths(@NotNull Set<Class> toolingExtensionClasses) {
    final Set<String> jarPaths = ContainerUtil.map2SetNotNull(toolingExtensionClasses, new Function<Class, String>() {
      @Override
      public String fun(Class aClass) {
        String path = PathManager.getJarPathForClass(aClass);
        return path == null ? null : PathUtil.getCanonicalPath(path);
      }
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
}
