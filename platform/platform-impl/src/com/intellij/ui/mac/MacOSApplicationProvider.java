// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.apple.eawt.Application;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.AboutAction;
import com.intellij.ide.actions.ShowSettingsAction;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.idea.IdeaApplication;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.Callback;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MacOSApplicationProvider {
  private static final Logger LOG = Logger.getInstance(MacOSApplicationProvider.class);

  private MacOSApplicationProvider() { }

  public static void initApplication() {
    if (SystemInfo.isMac) {
      try {
        Worker.initMacApplication();
      }
      catch (Throwable t) {
        LOG.warn(t);
      }
    }
  }

  private static class Worker {
    private static final AtomicBoolean ENABLED = new AtomicBoolean(true);
    @SuppressWarnings({"FieldCanBeLocal", "unused"}) private static Object UPDATE_CALLBACK_REF;

    static void initMacApplication() {
      Application application = Application.getApplication();

      application.setAboutHandler(event -> AboutAction.perform(getProject(false)));

      application.setPreferencesHandler(event -> {
        Project project = getProject(true);
        submit("Preferences", () -> ShowSettingsAction.perform(project));
      });

      application.setQuitHandler((event, response) -> {
        submit("Quit", () -> ApplicationManager.getApplication().exit());
        response.cancelQuit();
      });

      application.setOpenFileHandler(event -> {
        Project project = getProject(false);
        List<File> list = event.getFiles();
        if (list.isEmpty()) return;
        submit("OpenFile", () -> {
          if (ProjectUtil.tryOpenFileList(project, list, "MacMenu")) {
            IdeaApplication.disableProjectLoad();
          }
        });
      });

      if (JnaLoader.isLoaded()) {
        installAutoUpdateMenu();
      }
    }

    private static void installAutoUpdateMenu() {
      ID pool = Foundation.invoke("NSAutoreleasePool", "new");

      ID app = Foundation.invoke("NSApplication", "sharedApplication");
      ID menu = Foundation.invoke(app, Foundation.createSelector("menu"));
      ID item = Foundation.invoke(menu, Foundation.createSelector("itemAtIndex:"), 0);
      ID appMenu = Foundation.invoke(item, Foundation.createSelector("submenu"));

      ID checkForUpdatesClass = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSMenuItem"), "NSCheckForUpdates");
      Callback impl = new Callback() {
        @SuppressWarnings("unused")
        public void callback(ID self, String selector) {
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> {
            ActionManager am = ActionManager.getInstance();
            MouseEvent me = new MouseEvent(JOptionPane.getRootFrame(), MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false);
            am.tryToExecute(am.getAction("CheckForUpdate"), me, null, null, false);
          });
        }
      };
      UPDATE_CALLBACK_REF = impl;  // prevents the callback from being collected
      Foundation.addMethod(checkForUpdatesClass, Foundation.createSelector("checkForUpdates"), impl, "v");
      Foundation.registerObjcClassPair(checkForUpdatesClass);

      ID checkForUpdates = Foundation.invoke("NSCheckForUpdates", "alloc");
      Foundation.invoke(checkForUpdates, Foundation.createSelector("initWithTitle:action:keyEquivalent:"),
                        Foundation.nsString("Check for Updates..."), Foundation.createSelector("checkForUpdates"), Foundation.nsString(""));
      Foundation.invoke(checkForUpdates, Foundation.createSelector("setTarget:"), checkForUpdates);

      Foundation.invoke(appMenu, Foundation.createSelector("insertItem:atIndex:"), checkForUpdates, 1);
      Foundation.invoke(checkForUpdates, Foundation.createSelector("release"));

      Foundation.invoke(pool, Foundation.createSelector("release"));
    }

    private static Project getProject(boolean useDefault) {
      @SuppressWarnings("deprecation") Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
      if (project == null) {
        LOG.debug("MacMenu: no project in data context");
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        project = projects.length > 0 ? projects[0] : null;
        if (project == null && useDefault) {
          LOG.debug("MacMenu: use default project instead");
          project = ProjectManager.getInstance().getDefaultProject();
        }
      }
      LOG.debug("MacMenu: project = ", project);
      return project;
    }

    private static void submit(@NotNull String name, @NotNull Runnable task) {
      LOG.debug("MacMenu: on EDT = ", SwingUtilities.isEventDispatchThread(), "; ENABLED = ", ENABLED.get());
      if (!ENABLED.get()) {
        LOG.debug("MacMenu: disabled");
      }
      else {
        Component component = IdeFocusManager.getGlobalInstance().getFocusOwner();
        if (component != null && IdeKeyEventDispatcher.isModalContext(component)) {
          LOG.debug("MacMenu: component in modal context");
        }
        else {
          ENABLED.set(false);
          TransactionGuard.submitTransaction(ApplicationManager.getApplication(), () -> {
            try {
              LOG.debug("MacMenu: init ", name);
              task.run();
            }
            finally {
              LOG.debug("MacMenu: done ", name);
              ENABLED.set(true);
            }
          });
        }
      }
    }
  }
}