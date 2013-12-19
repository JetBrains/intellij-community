/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.execution.configurations.CommandLineTokenizer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.gradle.StartParameter;
import org.gradle.process.internal.JvmOptions;
import org.gradle.tooling.*;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
    prepare(result, id, settings, listener, extraJvmArgs, connection);
    return result;
  }

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  public BuildLauncher getBuildLauncher(@NotNull final ExternalSystemTaskId id,
                                        @NotNull ProjectConnection connection,
                                        @Nullable GradleExecutionSettings settings,
                                        @NotNull ExternalSystemTaskNotificationListener listener,
                                        @NotNull final List<String> vmOptions) {
    BuildLauncher result = connection.newBuild();
    prepare(result, id, settings, listener, vmOptions, connection);
    return result;
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public static void prepare(@NotNull LongRunningOperation operation,
                             @NotNull final ExternalSystemTaskId id,
                             @Nullable GradleExecutionSettings settings,
                             @NotNull final ExternalSystemTaskNotificationListener listener,
                             @NotNull List<String> extraJvmArgs,
                             @NotNull ProjectConnection connection) {
    prepare(operation, id, settings, listener, extraJvmArgs, connection,
            new OutputWrapper(listener, id, true), new OutputWrapper(listener, id, false));
  }


  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public static void prepare(@NotNull LongRunningOperation operation,
                             @NotNull final ExternalSystemTaskId id,
                             @Nullable GradleExecutionSettings settings,
                             @NotNull final ExternalSystemTaskNotificationListener listener,
                             @NotNull List<String> extraJvmArgs,
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
      BuildEnvironment buildEnvironment = getBuildEnvironment(connection);
      Collection<String> merged =
        buildEnvironment != null ? mergeJvmArgs(buildEnvironment.getJava().getJvmArguments(), jvmArgs) : jvmArgs;

      // filter nulls and empty strings
      List<String> filteredArgs = ContainerUtil.mapNotNull(merged, new Function<String, String>() {
        @Override
        public String fun(String s) {
          return StringUtil.isEmpty(s) ? null : s;
        }
      });

      operation.setJvmArguments(ArrayUtil.toStringArray(filteredArgs));
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

    // This is a workaround to get right base dir in case of 'PROJECT' setting used in case custom wrapper property file location
    // see org.gradle.wrapper.PathAssembler#getBaseDir for details
    String userDir = null;
    if (settings != null && settings.getDistributionType() == DistributionType.WRAPPED) {
      try {
        userDir = System.getProperty("user.dir");
        System.setProperty("user.dir", projectDir);
      }
      catch (Exception e) {
        // ignore
      }
    }
    ProjectConnection connection = getConnection(projectDir, settings);
    try {
      return f.fun(connection);
    }
    catch (Throwable e) {
      throw new ExternalSystemException(ExceptionUtil.getMessage(e));
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
        // ignore
      }
    }
  }

  public void ensureInstalledWrapper(@NotNull ExternalSystemTaskId id,
                                     @NotNull String projectPath,
                                     @NotNull GradleExecutionSettings settings,
                                     @NotNull ExternalSystemTaskNotificationListener listener) {

    // use it only for customized wrapper
    // TODO works correctly only or root project
    if (settings.getDistributionType() != DistributionType.WRAPPED) return;

    ProjectConnection connection = getConnection(projectPath, settings);
    try {
      BuildLauncher launcher = getBuildLauncher(id, connection, settings, listener, ContainerUtil.<String>newArrayList());
      try {
        final File tempFile = FileUtil.createTempFile("wrap", ".gradle");
        tempFile.deleteOnExit();
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
        FileUtil.writeToFile(tempFile, StringUtil.join(lines, SystemProperties.getLineSeparator()));
        launcher.withArguments("--init-script", tempFile.getAbsolutePath());
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

  private static List<String> mergeJvmArgs(Iterable<String> jvmArgs1, Iterable<String> jvmArgs2) {
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
                new DistributionFactoryExt(StartParameter.DEFAULT_GRADLE_USER_HOME).getWrappedDistribution(propertiesFile);
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

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public static void setInitScript(LongRunningOperation longRunningOperation) {
    try {
      InputStream stream = GradleProjectResolver.class.getResourceAsStream("/org/jetbrains/plugins/gradle/model/internal/init.gradle");
      if (stream == null) return;

      String jarPath = PathUtil.getCanonicalPath(PathUtil.getJarPathForClass(GradleProjectResolver.class));
      String s = FileUtil.loadTextAndClose(stream).replace("${JAR_PATH}", jarPath);

      final File tempFile = FileUtil.createTempFile("ijinit", '.' + GradleConstants.EXTENSION, true);
      FileUtil.writeToFile(tempFile, s);

      String[] buildExecutorArgs = new String[]{"--init-script", tempFile.getAbsolutePath()};
      longRunningOperation.withArguments(buildExecutorArgs);
    }
    catch (Exception e) {
      LOG.warn("Can't use IJ gradle init script", e);
    }
  }

  @Nullable
  private static BuildEnvironment getBuildEnvironment(@NotNull ProjectConnection connection) {
    try {
      return connection.getModel(BuildEnvironment.class);
    }
    catch (Exception e) {
      LOG.warn("can not get BuildEnvironment model", e);
      return null;
    }
  }
}
