// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win;

import com.intellij.ide.RecentProjectListActionProvider;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.SystemDock;

import java.io.File;
import java.util.List;


/**
 * @author Denis Fokin
 * @author Nikita Provotorov
 */
public final class WinDockDelegate implements SystemDock.Delegate {
  public static synchronized SystemDock.Delegate getInstance() {
    if (instance == null) {
      instance = new WinDockDelegate();
    }
    return instance;
  }

  @Override
  public void updateRecentProjectsMenu () {
    final List<AnAction> recentProjectActions = RecentProjectListActionProvider.getInstance().getActions(false);

    final String name = StringUtil.toLowerCase(ApplicationNamesInfo.getInstance().getProductName());
    final String launcherPath = PathManager.getBinPath() + File.separator + name + (SystemInfo.is64Bit ? "64" : "") + ".exe";

    final Task[] tasks = new Task[recentProjectActions.size()];
    for (int i = 0; i < recentProjectActions.size(); i++) {
      final ReopenProjectAction reopenProjectAction = (ReopenProjectAction)recentProjectActions.get(i);
      final String reopenProjectActionPath = reopenProjectAction.getProjectPath();
      tasks[i] = new Task(launcherPath, reopenProjectActionPath, reopenProjectAction.getTemplatePresentation().getText());
    }

    WinShellIntegration.clearRecentTasksList();
    WinShellIntegration.setRecentTasksList(tasks);
  }


  private WinDockDelegate() {}

  private static WinDockDelegate instance;
}
