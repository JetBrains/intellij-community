// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
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

  private static final @Nullable Delegate impl;

  static {
    SystemDock.Delegate delegate = null;

    Application app = ApplicationManager.getApplication();
    if (app != null && !app.isUnitTestMode()) {
      if (SystemInfoRt.isMac) {
        delegate = MacDockDelegate.getInstance();
      }
      else if (SystemInfoRt.isWindows) {
        delegate = WinDockDelegate.getInstance();
      }
    }

    impl = delegate;
  }

  public synchronized static void updateMenu() {
    try {
      if (impl != null) {
        impl.updateRecentProjectsMenu();
      }
    }
    catch (Throwable err) {
      Logger.getInstance(SystemDock.class).error(err);
    }
  }
}
