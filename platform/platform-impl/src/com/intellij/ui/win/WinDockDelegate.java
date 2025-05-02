// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win;

import com.intellij.ide.RecentProjectListActionProvider;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.wm.impl.SystemDock;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;
import org.jetbrains.annotations.SystemIndependent;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;


/**
 * @author Nikita Provotorov
 */
public final class WinDockDelegate implements SystemDock.Delegate {
  public static @Nullable WinDockDelegate getInstance() {
    return instance;
  }

  @Override
  public void updateRecentProjectsMenu() {
    final var stackTraceHolder = new Throwable("Asynchronously launched from here");

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        final var wsi = wsiFuture.get(30, TimeUnit.SECONDS);
        if (wsi == null) {
          return;
        }

        final List<AnAction> recentProjectActions = RecentProjectListActionProvider.getInstance().getActions(false);
        final @NotNull JumpTask @NotNull [] jumpTasks = convertToJumpTasks(recentProjectActions);

        wsi.postShellTask((final @NotNull WinShellIntegration.ShellContext ctx) -> {
          ctx.clearRecentTasksList();
          ctx.setRecentTasksList(jumpTasks);
        }).get();
      }
      catch (final InterruptedException e) {
        e.addSuppressed(stackTraceHolder);
        LOG.warn(e);
      }
      catch (final Throwable e) {
        e.addSuppressed(stackTraceHolder);
        LOG.error(e);
      }
    });
  }


  private WinDockDelegate(final @NotNull Future<@Nullable WinShellIntegration> wsiFuture) {
    this.wsiFuture = wsiFuture;
  }


  private static @NotNull JumpTask @NotNull [] convertToJumpTasks(final @NotNull List<AnAction> actions) {
    final String launcherFileName = ApplicationNamesInfo.getInstance().getScriptName() + "64.exe";
    final String launcherPath = Paths.get(PathManager.getBinPath(), launcherFileName).toString();

    final @NotNull JumpTask @NotNull [] result = new JumpTask[actions.size()];

    int i = 0;
    for (final var action : actions) {
      if (!(action instanceof ReopenProjectAction reopenProjectAction)) {
        LOG.debug("Failed to convert an action \"" + action + "\" to Jump Task: the action is not ReopenProjectAction");
        continue;
      }

      final @SystemIndependent String projectPath = reopenProjectAction.getProjectPath();
      final @SystemDependent String projectPathSystem = PathUtil.toSystemDependentName(projectPath);

      if (Strings.isEmptyOrSpaces(projectPathSystem)) {
        LOG.debug("Failed to convert a ReopenProjectAction \"" + reopenProjectAction +
                  "\" to Jump Task: path to the project is empty (\"" + projectPathSystem + "\")");
        continue;
      }

      final @NotNull String taskTitle;
      final @NotNull String taskTooltip;
      {
        final @Nullable String presentationText;
        final @Nullable String projectName;

        if (!Strings.isEmptyOrSpaces(presentationText = reopenProjectAction.getProjectDisplayName())) {
          taskTitle = presentationText;
          taskTooltip = presentationText + " (" + projectPathSystem + ")";
        }
        else if (!Strings.isEmptyOrSpaces(projectName = reopenProjectAction.getProjectNameToDisplay())) {
          taskTitle = projectName;
          taskTooltip = projectName + " (" + projectPathSystem + ")";
        }
        else {
          taskTitle = projectPathSystem;
          taskTooltip = projectPathSystem;
        }
      }

      final String taskArgs = "\"" + projectPathSystem + "\"";

      result[i++] = new JumpTask(taskTitle, launcherPath, taskArgs, taskTooltip);
    }

    if (i < result.length) {
      return Arrays.copyOf(result, i);
    }

    return result;
  }


  private final @NotNull Future<@Nullable WinShellIntegration> wsiFuture;


  private static final Logger LOG = Logger.getInstance(WinDockDelegate.class);
  private static final @Nullable WinDockDelegate instance;

  static {
    final var stackTraceHolder = new Throwable("Asynchronously launched from here");

    //                                                          Not AppExecutorUtil.getAppExecutorService() for class loading optimization
    final @NotNull Future<@Nullable WinShellIntegration> wsiFuture = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        if (!Registry.is("windows.jumplist")) {
          return null;
        }

        return WinShellIntegration.getInstance();
      }
      catch (final Throwable err) {
        err.addSuppressed(stackTraceHolder);
        LOG.error("Failed to initialize com.intellij.ui.win.WinShellIntegration instance", err);
        return null;
      }
    });

    instance = new WinDockDelegate(wsiFuture);
  }
}
