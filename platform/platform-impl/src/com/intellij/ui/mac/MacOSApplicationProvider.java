// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.CommandLineProcessor;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.AboutAction;
import com.intellij.ide.actions.ShowSettingsAction;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.idea.IdeStarter;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jna.Callback;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.desktop.OpenURIEvent;
import java.awt.desktop.OpenURIHandler;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@ApiStatus.Internal
public final class MacOSApplicationProvider {
  private static final Logger LOG = Logger.getInstance(MacOSApplicationProvider.class);

  private MacOSApplicationProvider() { }

  public static void initApplication(Logger log) {
    try {
      Worker.initMacApplication();
    }
    catch (Throwable t) {
      log.warn(t);
    }
  }

  private static final class Worker {
    private static final AtomicBoolean ENABLED = new AtomicBoolean(true);
    @SuppressWarnings({"FieldCanBeLocal", "unused"}) private static Object UPDATE_CALLBACK_REF;

    static void initMacApplication() {
      Desktop desktop = Desktop.getDesktop();

      desktop.setAboutHandler(event -> {
        if (LoadingState.COMPONENTS_LOADED.isOccurred()) {
          AboutAction.perform(getProject(false));
        }
      });

      desktop.setPreferencesHandler(event -> {
        if (LoadingState.COMPONENTS_LOADED.isOccurred()) {
          Project project = Objects.requireNonNull(getProject(true));
          submit("Preferences", () -> ShowSettingsAction.perform(project));
        }
      });

      desktop.setQuitHandler((event, response) -> {
        if (LoadingState.COMPONENTS_LOADED.isOccurred()) {
          submit("Quit", () -> ApplicationManager.getApplication().exit());
          response.cancelQuit();
        }
        else {
          response.performQuit();
        }
      });

      desktop.setOpenFileHandler(event -> {
        List<File> files = event.getFiles();
        if (files.isEmpty()) {
          return;
        }

        List<Path> list = ContainerUtil.map(files, file -> file.toPath());
        if (LoadingState.COMPONENTS_LOADED.isOccurred()) {
          Project project = getProject(false);
          submit("OpenFile", () -> ProjectUtil.tryOpenFiles(project, list, "MacMenu"));
        }
        else {
          IdeStarter.openFilesOnLoading(list);
        }
      });

      if (JnaLoader.isLoaded()) {
        installAutoUpdateMenu();
        installProtocolHandler();
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
          SwingUtilities.invokeLater(() -> {
            ActionManager actionManager = ActionManager.getInstance();
            MouseEvent mouseEvent = new MouseEvent(JOptionPane.getRootFrame(), MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false);
            actionManager.tryToExecute(actionManager.getAction("CheckForUpdate"), mouseEvent, null, null, false);
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

    private static @Nullable Project getProject(boolean useDefault) {
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

    private static void submit(String name, Runnable task) {
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
          ApplicationManager.getApplication().invokeLater(() -> {
            try {
              LOG.debug("MacMenu: init ", name);
              task.run();
            }
            finally {
              LOG.debug("MacMenu: done ", name);
              ENABLED.set(true);
            }
          }, ModalityState.NON_MODAL);
        }
      }
    }

    private static void installProtocolHandler() {
      ID mainBundle = Foundation.invoke("NSBundle", "mainBundle");
      ID urlTypes = Foundation.invoke(mainBundle, "objectForInfoDictionaryKey:", Foundation.nsString("CFBundleURLTypes"));
      if (urlTypes.equals(ID.NIL)) {
        BuildNumber build = ApplicationInfoImpl.getShadowInstance().getBuild();
        if (!build.isSnapshot()) {
          LOG.warn("No URL bundle (CFBundleURLTypes) is defined in the main bundle.\n" +
                   "To be able to open external links, specify protocols in the app layout section of the build file.\n" +
                   "Example: args.urlSchemes = [\"your-protocol\"] will handle following links: your-protocol://open?file=file&line=line");
        }
        return;
      }

      Desktop.getDesktop().setOpenURIHandler(new OpenURIHandler() {
        @Override
        public void openURI(OpenURIEvent event) {
          URI uri = event.getURI();
          String uriString = uri.toString();
          if ("open".equals(uri.getHost()) && new QueryStringDecoder(uri).parameters().get("file") != null) {
            uriString = CommandLineProcessor.SCHEME_INTERNAL + uriString;
          }

          if (LoadingState.APP_STARTED.isOccurred()) {
            CommandLineProcessor.processProtocolCommand(uriString);
          }
          else {
            IdeStarter.openUriOnLoading(uriString);
          }
        }
      });
    }
  }
}
