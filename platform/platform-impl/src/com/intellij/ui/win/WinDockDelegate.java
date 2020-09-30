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
 */
public final class WinDockDelegate implements SystemDock.Delegate {
  private static SystemDock.Delegate instance;

  public static synchronized SystemDock.Delegate getInstance() {
    if (instance == null) {
      instance = new WinDockDelegate();
    }
    return instance;
  }

  private WinDockDelegate() {}

  @Override
  public void updateRecentProjectsMenu () {
    List<AnAction> recentProjectActions = RecentProjectListActionProvider.getInstance().getActions(false);
    RecentTasks.clear();
    String name = StringUtil.toLowerCase(ApplicationNamesInfo.getInstance().getProductName());
    String launcher = RecentTasks.getShortenPath(PathManager.getBinPath() + File.separator + name + (SystemInfo.is64Bit ? "64" : "") + ".exe");
    Task[] tasks = new Task[recentProjectActions.size()];
    for (int i = 0; i < recentProjectActions.size(); i ++) {
      ReopenProjectAction rpa = (ReopenProjectAction)recentProjectActions.get(i);
      tasks[i] = new Task(launcher, RecentTasks.getShortenPath(rpa.getProjectPath()), rpa.getTemplatePresentation().getText());
    }
    RecentTasks.addTasks(tasks);
  }
}