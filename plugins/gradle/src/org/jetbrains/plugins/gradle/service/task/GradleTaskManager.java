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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemProgressEventUnsupportedImpl;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent;
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.service.execution.UnsupportedCancellationToken;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleBuildParticipant;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 3/14/13 5:09 PM
 */
public class GradleTaskManager implements ExternalSystemTaskManager<GradleExecutionSettings> {

  private static final Logger LOG = Logger.getInstance(GradleTaskManager.class);
  public static final Key<String> INIT_SCRIPT_KEY = Key.create("INIT_SCRIPT_KEY");

  private final GradleExecutionHelper myHelper = new GradleExecutionHelper();

  private final Map<ExternalSystemTaskId, CancellationTokenSource> myCancellationMap = ContainerUtil.newConcurrentMap();

  public GradleTaskManager() {
  }

  @Override
  public void executeTasks(@NotNull final ExternalSystemTaskId id,
                           @NotNull final List<String> taskNames,
                           @NotNull String projectPath,
                           @Nullable GradleExecutionSettings settings,
                           @Nullable final String jvmAgentSetup,
                           @NotNull final ExternalSystemTaskNotificationListener listener) throws ExternalSystemException {

    // TODO add support for external process mode
    if (ExternalSystemApiUtil.isInProcessMode(GradleConstants.SYSTEM_ID)) {
      for (GradleTaskManagerExtension gradleTaskManagerExtension : GradleTaskManagerExtension.EP_NAME.getExtensions()) {
        if (gradleTaskManagerExtension.executeTasks(id, taskNames, projectPath, settings, jvmAgentSetup, listener)) {
          return;
        }
      }
    }

    GradleExecutionSettings effectiveSettings =
      settings == null ? new GradleExecutionSettings(null, null, DistributionType.BUNDLED, false) : settings;
    Function<ProjectConnection, Void> f = connection -> {
      try {
        appendInitScriptArgument(taskNames, jvmAgentSetup, effectiveSettings);

        GradleVersion gradleVersion = GradleExecutionHelper.getGradleVersion(connection, id, listener);
        if (gradleVersion != null && gradleVersion.compareTo(GradleVersion.version("2.5")) < 0) {
          listener.onStatusChange(new ExternalSystemTaskExecutionEvent(
            id, new ExternalSystemProgressEventUnsupportedImpl(gradleVersion + " does not support executions view")));
        }

        for (GradleBuildParticipant buildParticipant : effectiveSettings.getExecutionWorkspace().getBuildParticipants()) {
          effectiveSettings.withArguments(GradleConstants.INCLUDE_BUILD_CMD_OPTION, buildParticipant.getProjectPath());
        }

        BuildLauncher launcher = myHelper.getBuildLauncher(id, connection, effectiveSettings, listener);
        launcher.forTasks(ArrayUtil.toStringArray(taskNames));

        if (gradleVersion != null && gradleVersion.compareTo(GradleVersion.version("2.1")) < 0) {
          myCancellationMap.put(id, new UnsupportedCancellationToken());
        }
        else {
          final CancellationTokenSource cancellationTokenSource = GradleConnector.newCancellationTokenSource();
          launcher.withCancellationToken(cancellationTokenSource.token());
          myCancellationMap.put(id, cancellationTokenSource);
        }
        try {
          launcher.run();
        }
        finally {
          myCancellationMap.remove(id);
        }
        return null;
      }
      catch (RuntimeException e) {
        LOG.debug("Gradle build launcher error", e);
        ExternalSystemException friendlyError = new GradleExecutionErrorHandler(e, projectPath, null).getUserFriendlyError();
        throw friendlyError == null ? e : friendlyError;
      }
    };
    myHelper.execute(projectPath, effectiveSettings, f);
  }

  public static void appendInitScriptArgument(@NotNull List<String> taskNames,
                                              @Nullable String jvmAgentSetup,
                                              @NotNull GradleExecutionSettings effectiveSettings) {
    final List<String> initScripts = ContainerUtil.newArrayList();
    final GradleProjectResolverExtension projectResolverChain = GradleProjectResolver.createProjectResolverChain(effectiveSettings);
    for (GradleProjectResolverExtension resolverExtension = projectResolverChain;
         resolverExtension != null;
         resolverExtension = resolverExtension.getNext()) {
      final String resolverClassName = resolverExtension.getClass().getName();
      resolverExtension.enhanceTaskProcessing(taskNames, jvmAgentSetup, script -> {
        if (StringUtil.isNotEmpty(script)) {
          ContainerUtil.addAllNotNull(
            initScripts,
            "//-- Generated by " + resolverClassName,
            script,
            "//");
        }
      });
    }

    final String initScript = effectiveSettings.getUserData(INIT_SCRIPT_KEY);
    if (StringUtil.isNotEmpty(initScript)) {
      ContainerUtil.addAll(
        initScripts,
        "//-- Additional script",
        initScript,
        "//");
    }

    if (!initScripts.isEmpty()) {
      try {
        File tempFile =
          GradleExecutionHelper.writeToFileGradleInitScript(StringUtil.join(initScripts, SystemProperties.getLineSeparator()));
        effectiveSettings.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, tempFile.getAbsolutePath());
      }
      catch (IOException e) {
        throw new ExternalSystemException(e);
      }
    }
  }

  @Override
  public boolean cancelTask(@NotNull ExternalSystemTaskId id, @NotNull ExternalSystemTaskNotificationListener listener)
    throws ExternalSystemException {

    // extension points are available only in IDE process
    if (ExternalSystemApiUtil.isInProcessMode(GradleConstants.SYSTEM_ID)) {
      for (GradleTaskManagerExtension gradleTaskManagerExtension : GradleTaskManagerExtension.EP_NAME.getExtensions()) {
        if (gradleTaskManagerExtension.cancelTask(id, listener)) return true;
      }
    }

    final CancellationTokenSource cancellationTokenSource = myCancellationMap.get(id);
    if (cancellationTokenSource != null) {
      cancellationTokenSource.cancel();
    }
    return true;
  }
}
