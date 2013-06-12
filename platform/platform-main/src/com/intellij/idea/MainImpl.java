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
package com.intellij.idea;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.PlatformUtilsCore;

import javax.swing.*;

@SuppressWarnings({"UnusedDeclaration", "HardCodedStringLiteral"})
public class MainImpl {
  private static final String LOG_CATEGORY = "#com.intellij.idea.Main";

  private MainImpl() { }

  /**
   * Called from PluginManager via reflection.
   */
  protected static void start(final String[] args) {
    System.setProperty(PlatformUtilsCore.PLATFORM_PREFIX_KEY, PlatformUtils.getPlatformPrefix(PlatformUtils.COMMUNITY_PREFIX));

    if (!Main.isHeadless()) {
      AppUIUtil.updateFrameClass();
      AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame());
      AppUIUtil.registerBundledFonts();

      boolean isNewConfigFolder = PathManager.ensureConfigFolderExists(true);
      if (isNewConfigFolder) {
        ConfigImportHelper.importConfigsTo(PathManager.getConfigPath());
      }
    }

    if (!StartupUtil.checkStartupPossible(args)) {   // It uses config folder!
      System.exit(Main.STARTUP_IMPOSSIBLE);
    }

    Logger.setFactory(LoggerFactory.getInstance());
    Logger LOG = Logger.getInstance(LOG_CATEGORY);
    StartupUtil.startLogging(LOG);

    // http://weblogs.java.net/blog/shan_man/archive/2005/06/improved_drag_g.html
    System.setProperty("sun.swing.enableImprovedDragGesture", "");

    StartupUtil.fixProcessEnvironment(LOG);
    StartupUtil.loadSystemLibraries(LOG);

    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        try {
          new IdeaApplication(args).run();
        }
        catch (PluginManager.StartupAbortedException e) {
          throw e;
        }
        catch (Throwable t) {
          throw new PluginManager.StartupAbortedException(t);
        }
      }
    });
  }
}
