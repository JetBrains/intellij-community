// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.MacDockDelegateInitializer;
import com.intellij.ui.win.WinDockDelegateInitializer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Denis Fokin
 * @author Nikita Provotorov
 */
public final class SystemDock {
  public interface Delegate {
    interface Initializer {
      void onUiInitialization();
      @Nullable Delegate onUiInitialized();
    }

    void updateRecentProjectsMenu();
  }


  synchronized public static void onUiInitialization() {
    ourDelegateInitializer.onUiInitialization();
  }

  synchronized public static void onUiInitialized() {
    final var delegateInitializer = ourDelegateInitializer;
    ourDelegateInitializer = null;

    ourDelegate = delegateInitializer.onUiInitialized();
  }


  synchronized public static void updateMenu() {
    if (ourDelegate != null) {
      ourDelegate.updateRecentProjectsMenu();
    }
  }


  private static Delegate.Initializer ourDelegateInitializer;
  private static @Nullable Delegate ourDelegate = null;


  static {
    SystemDock.Delegate.Initializer delegateInitializer = null;

    final Application app = ApplicationManager.getApplication();
    if (app != null && !app.isUnitTestMode()) {
      if (SystemInfo.isMac) {
        delegateInitializer = new MacDockDelegateInitializer();
      } else if (SystemInfo.isWindows) {
        delegateInitializer = new WinDockDelegateInitializer();
      }
    }

    ourDelegateInitializer = Objects.requireNonNullElseGet(delegateInitializer, () -> new Delegate.Initializer() {
      @Override
      public void onUiInitialization() {}

      @Override
      public @Nullable Delegate onUiInitialized() { return null; }
    });
  }
}
