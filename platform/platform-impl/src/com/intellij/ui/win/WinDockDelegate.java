// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win;

import com.intellij.ide.RecentProjectListActionProvider;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.SystemDock;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;


/**
 * @author Denis Fokin
 * @author Nikita Provotorov
 */
public final class WinDockDelegate implements SystemDock.Delegate {
  public static synchronized @Nullable SystemDock.Delegate getInstance() {
    if (instance == null) {
      final var winShellIntegration = WinShellIntegration.getInstance();
      if (winShellIntegration != null)
        instance = new WinDockDelegate(winShellIntegration);
    }
    return instance;
  }


  @Override
  public void updateRecentProjectsMenu() {
    final List<AnAction> recentProjectActions = RecentProjectListActionProvider.getInstance().getActions(false);

    final Task[] tasks = convertToJumpTasks(recentProjectActions);

    try {
      winShellIntegration.postShellTask((@NotNull final WinShellIntegration.ShellContext ctx) -> {
        ctx.clearRecentTasksList();
        ctx.setRecentTasksList(tasks);
      }).get();
    }
    catch (final InterruptedException e) {
      LOG.warn(e);
    }
    catch (final Throwable e) {
      LOG.error(e);
    }
  }


  private @NotNull Task @NotNull[] convertToJumpTasks(@NotNull final List<AnAction> actions)
  {
    final String name = StringUtil.toLowerCase(ApplicationNamesInfo.getInstance().getProductName());
    final String launcherPath = PathManager.getBinPath() + File.separator + name + (SystemInfo.is64Bit ? "64" : "") + ".exe";

    final Task[] result = new Task[actions.size()];

    int i = 0;
    for (; i < actions.size(); i++) {
      final var action = actions.get(i);
      if (!(action instanceof ReopenProjectAction))
        continue;

      final ReopenProjectAction reopenProjectAction = (ReopenProjectAction)action;

      final String reopenProjectActionPath = reopenProjectAction.getProjectPath();

      result[i] = new Task(launcherPath, reopenProjectActionPath, reopenProjectAction.getTemplatePresentation().getText());
    }

    if (i < result.length)
      return Arrays.copyOf(result, i);

    return result;
  }

  private WinDockDelegate(@NotNull final WinShellIntegration winShellIntegration)
  {
    this.winShellIntegration = winShellIntegration;
  }


  private final WinShellIntegration winShellIntegration;

  private static WinDockDelegate instance;
  private static final Logger LOG = Logger.getInstance(WinDockDelegate.class);
}
