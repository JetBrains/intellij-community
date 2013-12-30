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
package org.jetbrains.plugins.gradle.service.task;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.task.AbstractExternalSystemTaskManager;
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 3/14/13 5:09 PM
 */
public class GradleTaskManager extends AbstractExternalSystemTaskManager<GradleExecutionSettings>
  implements ExternalSystemTaskManager<GradleExecutionSettings> {

  private final GradleExecutionHelper myHelper = new GradleExecutionHelper();

  /**
   * TODO: remove the method in 13.1
   */
  @Override
  public void executeTasks(@NotNull ExternalSystemTaskId id,
                           @NotNull List<String> taskNames,
                           @NotNull String projectPath,
                           @Nullable GradleExecutionSettings settings,
                           @Nullable String vmOptions,
                           @Nullable String debuggerSetup,
                           @NotNull ExternalSystemTaskNotificationListener listener) throws ExternalSystemException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void executeTasks(@NotNull final ExternalSystemTaskId id,
                           @NotNull final List<String> taskNames,
                           @NotNull String projectPath,
                           @Nullable final GradleExecutionSettings settings,
                           @NotNull final List<String> vmOptions,
                           @NotNull final List<String> scriptParameters,
                           @Nullable final String debuggerSetup,
                           @NotNull final ExternalSystemTaskNotificationListener listener) throws ExternalSystemException {

    if (settings != null) {
      myHelper.ensureInstalledWrapper(id, projectPath, settings, listener);
    }

    // TODO add support for external process mode
    if (ExternalSystemApiUtil.isInProcessMode(GradleConstants.SYSTEM_ID)) {
      for (GradleTaskManagerExtension gradleTaskManagerExtension : GradleTaskManagerExtension.EP_NAME.getExtensions()) {
        if (gradleTaskManagerExtension.executeTasks(
          id, taskNames, projectPath, settings, vmOptions, scriptParameters, debuggerSetup, listener)) {
          return;
        }
      }
    }

    Function<ProjectConnection, Void> f = new Function<ProjectConnection, Void>() {
      @Override
      public Void fun(ProjectConnection connection) {
        BuildLauncher launcher = myHelper.getBuildLauncher(id, connection, settings, listener, vmOptions);
        if (!StringUtil.isEmpty(debuggerSetup)) {
          try {
            final File tempFile = FileUtil.createTempFile("init", ".gradle");
            tempFile.deleteOnExit();
            final String[] lines = {
              "gradle.taskGraph.beforeTask { Task task ->",
              "    if (task instanceof JavaForkOptions) {",
              "        task.jvmArgs '" + debuggerSetup.trim() + '\'',
              "}}",
            };
            FileUtil.writeToFile(tempFile, StringUtil.join(lines, SystemProperties.getLineSeparator()));

            scriptParameters.add("--init-script");
            scriptParameters.add(tempFile.getAbsolutePath());
          }
          catch (IOException e) {
            throw new ExternalSystemException(e);
          }
        }

        if (!scriptParameters.isEmpty()) {
          launcher.withArguments(ArrayUtil.toStringArray(scriptParameters));
        }
        launcher.forTasks(ArrayUtil.toStringArray(taskNames));
        launcher.run();
        return null;
      }
    };
    myHelper.execute(projectPath, settings, f);
  }

  @Override
  public boolean cancelTask(@NotNull ExternalSystemTaskId id, @NotNull ExternalSystemTaskNotificationListener listener)
    throws ExternalSystemException {

    for (GradleTaskManagerExtension gradleTaskManagerExtension : GradleTaskManagerExtension.EP_NAME.getExtensions()) {
      if (gradleTaskManagerExtension.cancelTask(id, listener)) return true;
    }

    // TODO replace with cancellation gradle API invocation when it will be ready, see http://issues.gradle.org/browse/GRADLE-1539
    if (!ExternalSystemApiUtil.isInProcessMode(GradleConstants.SYSTEM_ID)) {
      listener.onStatusChange(new ExternalSystemTaskNotificationEvent(id, "Cancelling the task...\n"));
      System.exit(0);
    }
    return false;
  }
}
