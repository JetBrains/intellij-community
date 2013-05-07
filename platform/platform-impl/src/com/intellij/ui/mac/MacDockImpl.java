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

import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.util.io.UniqueNameBuilder;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.SystemProperties;
import com.sun.jna.Callback;

import java.io.File;

import static com.intellij.ui.mac.foundation.Foundation.*;

/**
 * @author Denis Fokin
 */
public class MacDockImpl {

  private static boolean initialized = false;

  private MacDockImpl() {}

  private static final Callback RECENT_PROJECT_FROM_DOCK_MENU_SELECTED = new Callback() {
    @SuppressWarnings("UnusedDeclaration")
    public void callback(ID self, String selector, ID sender) {
      NSAutoreleasePool pool = new NSAutoreleasePool();
      try {
        final String recentProjectPathToOpen = toStringViaUTF8(invoke(sender, "representedObject"));
        LaterInvocator.invokeLater(new Runnable() {
          @Override
          public void run() {
            ProjectUtil.openProject(recentProjectPathToOpen, null, false);
          }
        });
      }
      finally {
        pool.drain();
      }
    }
  };

  private static final Callback APPLICATION_DOCK_MENU = new Callback() {
    @SuppressWarnings("UnusedDeclaration")
    public ID callback(ID self, String selector, ID application) {

      final AnAction[] recentProjectActions = RecentProjectsManagerBase.getInstance().getRecentProjectsActions(false);

      if (recentProjectActions.length == 0) return null;

      NSAutoreleasePool pool = new NSAutoreleasePool();
      try {

        ID recentProjectsMenuWrapperMenu = invoke(invoke("NSMenu", "alloc"), "init");

        ID recentProjectsMenuWrapperItem = invoke(invoke("NSMenuItem", "alloc"),
                                                  "initWithTitle:action:keyEquivalent:",
                                                  nsString("Recent projects"),
                                                  null, nsString(""));

        invoke(recentProjectsMenuWrapperMenu, "addItem:", recentProjectsMenuWrapperItem);

        ID recentProjectsMenu = invoke(invoke("NSMenu", "alloc"),
                                       "initWithTitle:", nsString("Recent projects"));

        invoke(recentProjectsMenuWrapperItem, "setSubmenu:", recentProjectsMenu);

        final UniqueNameBuilder<ReopenProjectAction> myPathShortener =
          new UniqueNameBuilder<ReopenProjectAction>(SystemProperties.getUserHome(), File.separator, 40);
        for (AnAction action : recentProjectActions) {
          addItemToDockRecentProjectsMenu(recentProjectsMenu, (ReopenProjectAction)action);
        }

        return recentProjectsMenuWrapperMenu;
      }
      finally {
        pool.drain();
      }
    }
  };

  private static void addItemToDockRecentProjectsMenu(ID recentProjectsMenu,
                                                      ReopenProjectAction reopenProjectAction) {
    ID recentProjectsMenuItem = invoke(invoke("NSMenuItem", "alloc"),
                                       "initWithTitle:action:keyEquivalent:",
                                       nsString(reopenProjectAction.getProjectName()),
                                       createSelector("handleDockRecentProject:"), nsString(""));
    invoke(recentProjectsMenuItem, "setRepresentedObject:", nsString(reopenProjectAction.getProjectPath()));

    invoke(recentProjectsMenu, "addItem:", recentProjectsMenuItem);
  }

  synchronized public static void initialize() {
    if (initialized) return;

    final ID delegateClass = allocateObjcClassPair(getObjcClass("NSObject"), "NSApplicationDelegate");

    if (!addMethod(delegateClass, createSelector("applicationDockMenu:"), APPLICATION_DOCK_MENU, "@:@")) {
      throw new RuntimeException("Unable to add method to objective-c delegate class!");
    }
    if (!addMethod(delegateClass, createSelector("handleDockRecentProject:"), RECENT_PROJECT_FROM_DOCK_MENU_SELECTED, "@:@")) {
      throw new RuntimeException("Unable to add method to objective-c delegate class!");
    }

    registerObjcClassPair(delegateClass);
    ID delegate = invoke(invoke(delegateClass, "alloc"), "init");

    invoke(invoke("NSApplication", "sharedApplication"), "setDelegate:", delegate);

    initialized = true;
  }
}
