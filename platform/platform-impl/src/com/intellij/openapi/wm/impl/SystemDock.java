// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.MacDockDelegate;
import com.intellij.ui.win.WinDockDelegate;

/**
 * @author Denis Fokin
 */
public final class SystemDock {
  private static final Delegate ourDelegate;

  static {
    Delegate delegate = null;

    Application app = ApplicationManager.getApplication();
    if (app != null && !app.isUnitTestMode()) {
      if (SystemInfo.isMac) {
        delegate = MacDockDelegate.getInstance();
      }
      else if (SystemInfo.isWin7OrNewer && Registry.is("windows.jumplist")) {
        delegate = WinDockDelegate.getInstance();
      }
    }

    ourDelegate = delegate;
  }

  public static void updateMenu() {
    if (ourDelegate != null) {
      ourDelegate.updateRecentProjectsMenu();
    }
  }

  public interface Delegate {
    void updateRecentProjectsMenu();
  }
}
