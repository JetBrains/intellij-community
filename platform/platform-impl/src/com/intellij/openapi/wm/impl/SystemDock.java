// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.MacDockDelegate;
import com.intellij.ui.win.WinDockDelegate;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Fokin
 * @author Nikita Provotorov
 */
public final class SystemDock {
  public interface Delegate {
    void updateRecentProjectsMenu();
  }


  synchronized public static void updateMenu() {
    try {
      if (ourDelegate != null) {
        ourDelegate.updateRecentProjectsMenu();
      }
    }
    catch (Throwable err) {
      log.error(err);
    }
  }


  private static final Logger log = Logger.getInstance(SystemDock.class);
  private static final @Nullable Delegate ourDelegate;

  static {
    SystemDock.Delegate delegate = null;

    final Application app = ApplicationManager.getApplication();
    if (app != null && !app.isUnitTestMode()) {
      if (SystemInfo.isMac) {
        delegate = MacDockDelegate.getInstance();
      }
      else if (SystemInfo.isWindows) {
        delegate = WinDockDelegate.getInstance();
      }
    }

    ourDelegate = delegate;
  }
}
