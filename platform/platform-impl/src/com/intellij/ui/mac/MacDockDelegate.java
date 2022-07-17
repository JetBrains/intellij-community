// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.intellij.ide.DataManager;
import com.intellij.ide.RecentProjectListActionProvider;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.impl.SystemDock;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author Denis Fokin
 */
public final class MacDockDelegate implements SystemDock.Delegate {
  private static final Logger LOG = Logger.getInstance(MacDockDelegate.class);

  private static boolean initialized = false;
  private static final SystemDock.Delegate instance = new MacDockDelegate();

  private static final PopupMenu dockMenu = new PopupMenu("DockMenu");
  private static final Menu recentProjectsMenu = new Menu("Recent Projects");

  private MacDockDelegate() { }

  private static void initDockMenu() {
    dockMenu.add(recentProjectsMenu);

    try {
      Class<?> appClass = Class.forName("com.apple.eawt.Application");
      Object application = appClass.getMethod("getApplication").invoke(null);
      appClass.getMethod("setDockMenu", PopupMenu.class).invoke(application, dockMenu);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private static void activateApplication() {
    try {
      Class<?> appClass = Class.forName("com.apple.eawt.Application");
      Object application = appClass.getMethod("getApplication").invoke(null);
      appClass.getMethod("requestForeground", boolean.class).invoke(application, false);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  public void updateRecentProjectsMenu () {
    RecentProjectsManager projectsManager = RecentProjectsManager.getInstance();
    if (projectsManager == null) {
      return;
    }
    List<AnAction> recentProjectActions = RecentProjectListActionProvider.getInstance().getActions(false);
    recentProjectsMenu.removeAll();

    for (AnAction action : recentProjectActions) {
      MenuItem menuItem = new MenuItem(((ReopenProjectAction)action).getProjectNameToDisplay());
      menuItem.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          // Newly opened project won't become an active window, if another application is currently active.
          // This is not what user expects, so we activate our application explicitly.
          activateApplication();
          ActionUtil.performActionDumbAwareWithCallbacks(action, AnActionEvent.createFromAnAction(action, null, ActionPlaces.DOCK_MENU, DataManager.getInstance().getDataContext(null)));
        }
      });
      recentProjectsMenu.add(menuItem);
    }
  }

  synchronized public static SystemDock.Delegate getInstance() {
    if (!initialized) {
      initDockMenu();
      initialized = true;
    }
    return instance;
  }
}
