// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win;

import com.intellij.ide.RecentProjectListActionProvider;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.wm.impl.SystemDock;
import com.intellij.util.PathUtil;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;
import org.jetbrains.annotations.SystemIndependent;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;


/**
 * @author Denis Fokin
 * @author Nikita Provotorov
 */
public final class WinDockDelegate implements SystemDock.Delegate {
  public static @Nullable WinDockDelegate getInstance() {
    return instance;
  }

  @Override
  public void updateRecentProjectsMenu() {
    final List<AnAction> recentProjectActions = RecentProjectListActionProvider.getInstance().getActions(false);

    final @NotNull Task @NotNull [] tasks = convertToJumpTasks(recentProjectActions);

    try {
      wsi.postShellTask((@NotNull final WinShellIntegration.ShellContext ctx) -> {
        ctx.clearRecentTasksList();
        ctx.setRecentTasksList(tasks);
      }).get();

      if (tasks.length < recentProjectActions.size()) {
        LOG.warn("Only " + tasks.length + " out of " + recentProjectActions.size() + " actions are displayed in Jump list");
      }
    }
    catch (final InterruptedException e) {
      LOG.warn(e);
    }
    catch (final Throwable e) {
      LOG.error(e);
    }
  }


  private WinDockDelegate(@NotNull final WinShellIntegration wsi) {
    this.wsi = wsi;
  }


  private static @NotNull Task @NotNull [] convertToJumpTasks(final @NotNull List<AnAction> actions) {
    final String launcherFileName = ApplicationNamesInfo.getInstance().getScriptName() + (CpuArch.isIntel64() ? "64" : "") + ".exe";
    final String launcherPath = Paths.get(PathManager.getBinPath(), launcherFileName).toString();

    final @NotNull Task @NotNull [] result = new Task[actions.size()];

    int i = 0;
    for (final var action : actions) {
      if (!(action instanceof ReopenProjectAction)) {
        continue;
      }

      final ReopenProjectAction reopenProjectAction = (ReopenProjectAction)action;

      final @SystemIndependent String projectPath = reopenProjectAction.getProjectPath();
      if (Strings.isEmptyOrSpaces(projectPath)) {
        continue;
      }

      final @SystemDependent String projectPathSystem = PathUtil.toSystemDependentName(projectPath);
      assert !Strings.isEmptyOrSpaces(projectPathSystem);

      final @NotNull String taskTitle;
      {
        final @Nullable String presentationText;
        final @Nullable String projectName;

        if (!Strings.isEmptyOrSpaces(presentationText = reopenProjectAction.getTemplatePresentation().getText())) {
          taskTitle = presentationText;
        }
        else if (!Strings.isEmptyOrSpaces(projectName = reopenProjectAction.getProjectName())) {
          taskTitle = projectName;
        }
        else {
          taskTitle = projectPathSystem;
        }
      }

      final String taskArgs = "\"" + projectPathSystem + "\"";

      result[i++] = new Task(launcherPath, taskArgs, taskTitle);
    }

    if (i < result.length) {
      return Arrays.copyOf(result, i);
    }

    return result;
  }


  private final @NotNull WinShellIntegration wsi;


  private static final Logger LOG = Logger.getInstance(WinDockDelegate.class);
  private static final @Nullable WinDockDelegate instance;

  static {
    @Nullable WinDockDelegate instanceInitializer = null;

    try {
      if (Registry.is("windows.jumplist")) {
        final @Nullable var wsi = WinShellIntegration.getInstance();
        if (wsi != null) {
          instanceInitializer = new WinDockDelegate(WinShellIntegration.getInstance());
        }
      }
    }
    catch (Throwable err) {
      LOG.error(err);
      instanceInitializer = null;
    }

    instance = instanceInitializer;
  }
}
