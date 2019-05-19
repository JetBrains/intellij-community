// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.task;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerConfiguration;
import com.intellij.openapi.externalSystem.task.BaseExternalSystemTaskManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleBuildParticipant;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.util.containers.ContainerUtil.*;
import static org.jetbrains.plugins.gradle.util.GradleUtil.determineRootProject;

/**
 * @author Denis Zhdanov
 */
public class GradleTaskManager extends BaseExternalSystemTaskManager<GradleExecutionSettings> {

  public static final Key<String> INIT_SCRIPT_KEY = Key.create("INIT_SCRIPT_KEY");
  public static final Key<String> INIT_SCRIPT_PREFIX_KEY = Key.create("INIT_SCRIPT_PREFIX_KEY");
  private static final Logger LOG = Logger.getInstance(GradleTaskManager.class);
  private final GradleExecutionHelper myHelper = new GradleExecutionHelper();

  private final Map<ExternalSystemTaskId, CancellationTokenSource> myCancellationMap = newConcurrentMap();

  public GradleTaskManager() {
  }

  @Override
  public void executeTasks(@NotNull final ExternalSystemTaskId id,
                           @NotNull final List<String> taskNames,
                           @NotNull String projectPath,
                           @Nullable GradleExecutionSettings settings,
                           @Nullable final String jvmAgentSetup,
                           @NotNull final ExternalSystemTaskNotificationListener listener) throws ExternalSystemException {
    if (ExternalSystemApiUtil.isInProcessMode(GradleConstants.SYSTEM_ID)) {
      for (GradleTaskManagerExtension gradleTaskManagerExtension : GradleTaskManagerExtension.EP_NAME.getExtensions()) {
        if (gradleTaskManagerExtension.executeTasks(id, taskNames, projectPath, settings, jvmAgentSetup, listener)) {
          return;
        }
      }
    }

    GradleExecutionSettings effectiveSettings =
      settings == null ? new GradleExecutionSettings(null, null, DistributionType.BUNDLED, false) : settings;

    ForkedDebuggerConfiguration forkedDebuggerSetup = ForkedDebuggerConfiguration.parse(jvmAgentSetup);
    if (forkedDebuggerSetup != null && isGradleScriptDebug(settings)) {
      effectiveSettings.withVmOption(forkedDebuggerSetup.getJvmAgentSetup(isJdk9orLater(effectiveSettings.getJavaHome())));
    }

    CancellationTokenSource cancellationTokenSource = GradleConnector.newCancellationTokenSource();
    myCancellationMap.put(id, cancellationTokenSource);
    Function<ProjectConnection, Void> f = connection -> {
      try {
        appendInitScriptArgument(taskNames, jvmAgentSetup, effectiveSettings);
        try {
          for (GradleBuildParticipant buildParticipant : effectiveSettings.getExecutionWorkspace().getBuildParticipants()) {
            effectiveSettings.withArguments(GradleConstants.INCLUDE_BUILD_CMD_OPTION, buildParticipant.getProjectPath());
          }

          List<String> args = newSmartList();
          for (Iterator<String> iterator = effectiveSettings.getArguments().iterator(); iterator.hasNext(); ) {
            String arg = iterator.next();
            if ("--args".equals(arg) && iterator.hasNext()) {
              args.add("--args");
              args.add(iterator.next());
            }
          }
          String[] tasksArray;
          if (!args.isEmpty()) {
            // todo append --args only after JavaExec tasks
            tasksArray = taskNames.stream().flatMap(task -> concat(Collections.singletonList(task), args).stream()).toArray(String[]::new);
          }
          else {
            tasksArray = ArrayUtil.toStringArray(taskNames);
          }

          BuildLauncher launcher = myHelper.getBuildLauncher(id, connection, effectiveSettings, listener);
          launcher.forTasks(tasksArray);

          launcher.withCancellationToken(cancellationTokenSource.token());
          launcher.run();
        }
        finally {
          myCancellationMap.remove(id);
        }
        return null;
      }
      catch (RuntimeException e) {
        LOG.debug("Gradle build launcher error", e);
        BuildEnvironment buildEnvironment = GradleExecutionHelper.getBuildEnvironment(connection, id, listener, cancellationTokenSource);
        final GradleProjectResolverExtension projectResolverChain = GradleProjectResolver.createProjectResolverChain(effectiveSettings);
        throw projectResolverChain.getUserFriendlyError(buildEnvironment, e, projectPath, null);
      }
    };
    if (effectiveSettings.getDistributionType() == DistributionType.WRAPPED) {
      myHelper.ensureInstalledWrapper(id, determineRootProject(projectPath), effectiveSettings, listener, cancellationTokenSource.token());
    }
    myHelper.execute(projectPath, effectiveSettings, f);
  }

  protected boolean isGradleScriptDebug(@Nullable GradleExecutionSettings settings) {
    return Optional.ofNullable(settings)
      .map(s -> s.getUserData(GradleRunConfiguration.DEBUG_FLAG_KEY))
      .orElse(false);
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

  public static void appendInitScriptArgument(@NotNull List<String> taskNames,
                                              @Nullable String jvmAgentSetup,
                                              @NotNull GradleExecutionSettings effectiveSettings) {
    final List<String> initScripts = new ArrayList<>();
    final GradleProjectResolverExtension projectResolverChain = GradleProjectResolver.createProjectResolverChain(effectiveSettings);
    for (GradleProjectResolverExtension resolverExtension = projectResolverChain;
         resolverExtension != null;
         resolverExtension = resolverExtension.getNext()) {
      final String resolverClassName = resolverExtension.getClass().getName();
      Consumer<String> initScriptConsumer = script -> {
        if (StringUtil.isNotEmpty(script)) {
          addAllNotNull(
            initScripts,
            "//-- Generated by " + resolverClassName,
            script,
            "//");
        }
      };
      boolean isTestExecution = Boolean.TRUE == effectiveSettings.getUserData(GradleConstants.RUN_TASK_AS_TEST);
      resolverExtension.enhanceTaskProcessing(taskNames, jvmAgentSetup, initScriptConsumer, isTestExecution);
    }

    if (!initScripts.isEmpty()) {
      try {
        File tempFile = GradleExecutionHelper.writeToFileGradleInitScript(
          StringUtil.join(initScripts, SystemProperties.getLineSeparator()), "ijresolvers");
        effectiveSettings.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, tempFile.getAbsolutePath());
      }
      catch (IOException e) {
        ExternalSystemException systemException = new ExternalSystemException(e);
        systemException.initCause(e);
        throw systemException;
      }
    }

    final String initScript = effectiveSettings.getUserData(INIT_SCRIPT_KEY);
    if (StringUtil.isNotEmpty(initScript)) {
      try {
        String initScriptPrefix = effectiveSettings.getUserData(INIT_SCRIPT_PREFIX_KEY);
        if (StringUtil.isEmpty(initScriptPrefix)) {
          initScriptPrefix = "ijmiscinit";
        }
        else {
          initScriptPrefix = FileUtil.sanitizeFileName(initScriptPrefix);
        }
        File tempFile = GradleExecutionHelper.writeToFileGradleInitScript(initScript, initScriptPrefix);
        effectiveSettings.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, tempFile.getAbsolutePath());
      }
      catch (IOException e) {
        ExternalSystemException externalSystemException = new ExternalSystemException(e);
        externalSystemException.initCause(e);
        throw externalSystemException;
      }
    }
  }
}
