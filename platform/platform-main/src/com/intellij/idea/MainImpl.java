// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.util.PlatformUtils;

import javax.swing.*;

@SuppressWarnings({"UnusedDeclaration"})
public class MainImpl {
  private MainImpl() { }

  /**
   * Called from PluginManager via reflection.
   */
  protected static void start(final String[] args) {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, PlatformUtils.getPlatformPrefix(PlatformUtils.IDEA_CE_PREFIX));

    StartupUtil.prepareAndStart(args, newConfigFolder -> {
      if (newConfigFolder && !ConfigImportHelper.isConfigImported()) {
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> {
          PluginManager.installExceptionHandler();
          StartupUtil.runStartupWizard();
          IdeaApplication.initApplication(args);
        });
      }
      else {
        IdeaApplication.initApplication(args);
      }
    });
  }
}