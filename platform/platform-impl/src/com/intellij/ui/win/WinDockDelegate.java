/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui.win;

import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.SystemDock;

import java.io.File;
import java.util.Locale;

/**
 * @author Denis Fokin
 */
public class WinDockDelegate implements SystemDock.Delegate {
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
    final AnAction[] recentProjectActions = RecentProjectsManager.getInstance().getRecentProjectsActions(false);
    RecentTasks.clear();
    String name = ApplicationNamesInfo.getInstance().getProductName().toLowerCase(Locale.US);
    String launcher = RecentTasks.getShortenPath(PathManager.getBinPath() + File.separator + name + (SystemInfo.is64Bit ? "64" : "") + ".exe");
    Task[] tasks = new Task[recentProjectActions.length];
    for (int i = 0; i < recentProjectActions.length; i ++) {
      ReopenProjectAction rpa = (ReopenProjectAction)recentProjectActions[i];
      tasks[i] = new Task(launcher, RecentTasks.getShortenPath(rpa.getProjectPath()), rpa.getTemplatePresentation().getText());
    }
    RecentTasks.addTasks(tasks);
  }
}