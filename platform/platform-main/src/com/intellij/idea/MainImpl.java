/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.idea;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

@SuppressWarnings({"HardCodedStringLiteral", "UseOfSystemOutOrSystemErr", "UnusedDeclaration"})
public class MainImpl {
  private static final String LOG_CATEGORY = "#com.intellij.idea.Main";

  private MainImpl() { }

  /**
   * Is called from PluginManager via reflection.
   */
  @SuppressWarnings("UnusedDeclaration")
  protected static void start(final String[] args) {
    if (System.getProperty("idea.platform.prefix") == null) {
      System.setProperty("idea.platform.prefix", "Idea");
    }

    StartupUtil.isHeadless = Main.isHeadless(args);
    if (!StartupUtil.isHeadless) {
      AppUIUtil.updateFrameClass();
      AppUIUtil.updateFrameIcon(JOptionPane.getRootFrame());

      UIUtil.initDefaultLAF();

      final boolean isNewConfigFolder = PathManager.ensureConfigFolderExists(true);
      if (isNewConfigFolder) {
        ConfigImportHelper.importConfigsTo(PathManager.getConfigPath());
      }
    }

    if (!StartupUtil.checkStartupPossible(args)) {   // It uses config folder!
      System.exit(-1);
    }

    Logger.setFactory(LoggerFactory.getInstance());

    final Logger LOG = Logger.getInstance(LOG_CATEGORY);

    Runtime.getRuntime().addShutdownHook(new Thread("Shutdown hook - logging") {
      public void run() {
        LOG.info(
          "------------------------------------------------------ IDE SHUTDOWN ------------------------------------------------------");
      }
    });
    LOG.info("------------------------------------------------------ IDE STARTED ------------------------------------------------------");

    final ApplicationInfo appInfo = ApplicationInfoImpl.getShadowInstance();
    final ApplicationNamesInfo namesInfo = ApplicationNamesInfo.getInstance();
    LOG.info("IDE: " + namesInfo.getFullProductName() + " (build #" + appInfo.getBuild() + ", " +
             DateFormatUtil.formatBuildDate(appInfo.getBuildDate()) + ")");
    LOG.info("JRE: " + System.getProperty("java.runtime.version", "-") + " (" + System.getProperty("java.vendor", "-") + ")");
    LOG.info("JVM: " + System.getProperty("java.vm.version", "-") + " (" + System.getProperty("java.vm.vendor", "-") + ")");

    _main(args);
  }

  protected static void _main(final String[] args) {
    // http://weblogs.java.net/blog/shan_man/archive/2005/06/improved_drag_g.html
    System.setProperty("sun.swing.enableImprovedDragGesture", "");

    if (SystemInfo.isWindows && !SystemInfo.isWindows9x) {
      final Logger LOG = Logger.getInstance(LOG_CATEGORY);
      try {
        if (SystemInfo.isAMD64) {
          System.loadLibrary("focuskiller64");
        }
        else {
          System.loadLibrary("focuskiller");
        }
        LOG.info("Using \"FocusKiller\" library to prevent focus stealing.");
      }
      catch (Throwable e) {
        LOG.info("\"FocusKiller\" library not found or there were problems loading it.", e);
      }
    }

    startApplication(args);
  }

  private static void startApplication(final String[] args) {
    final IdeaApplication app = new IdeaApplication(args);
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        app.run();
      }
    });
  }
}