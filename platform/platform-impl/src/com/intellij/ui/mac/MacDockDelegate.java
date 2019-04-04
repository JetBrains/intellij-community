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
package com.intellij.ui.mac;

import com.intellij.ide.DataManager;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.impl.SystemDock;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author Denis Fokin
 */
public class MacDockDelegate implements SystemDock.Delegate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.mac.MacDockDelegate");

  private static boolean initialized = false;
  private static final SystemDock.Delegate instance = new MacDockDelegate();

  private static final PopupMenu dockMenu = new PopupMenu("DockMenu");
  private static final Menu recentProjectsMenu = new Menu("Recent projects");

  private MacDockDelegate() {}

  private static void initDockMenu() {
    dockMenu.add(recentProjectsMenu);

    try {
      getAppMethod("setDockMenu", PopupMenu.class).invoke(getApp(), dockMenu);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  public void updateRecentProjectsMenu () {
    RecentProjectsManager projectsManager = RecentProjectsManager.getInstance();
    if (projectsManager == null) return;
    final AnAction[] recentProjectActions = projectsManager.getRecentProjectsActions(false);
    recentProjectsMenu.removeAll();

    for (final AnAction action : recentProjectActions) {
      MenuItem menuItem = new MenuItem(((ReopenProjectAction)action).getProjectName());
      menuItem.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          action.actionPerformed(AnActionEvent.createFromAnAction(action, null, ActionPlaces.DOCK_MENU, DataManager.getInstance().getDataContext(null)));
        }
      });
      recentProjectsMenu.add(menuItem);
    }
  }

  private static Object getApp() throws NoSuchMethodException, ClassNotFoundException, InvocationTargetException, IllegalAccessException {
    return getAppClass().getMethod("getApplication").invoke(null);
  }

  private static Method getAppMethod(final String name, Class... args) throws NoSuchMethodException, ClassNotFoundException {
    return getAppClass().getMethod(name, args);
  }

  private static Class<?> getAppClass() throws ClassNotFoundException {
    return Class.forName("com.apple.eawt.Application");
  }

  synchronized public static SystemDock.Delegate getInstance() {
    if (!initialized) {
      initDockMenu();
      initialized = true;
    }
    return instance;
  }
}
