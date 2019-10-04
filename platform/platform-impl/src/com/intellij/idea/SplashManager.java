// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ActivitySubNames;
import com.intellij.diagnostic.ParallelActivity;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.Splash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public final class SplashManager {
  @SuppressWarnings("SpellCheckingInspection")
  public static final String NO_SPLASH = "nosplash";

  private static Splash SPLASH_WINDOW;

  public static void show(@NotNull String[] args) {
    if (Boolean.getBoolean(NO_SPLASH)) {
      return;
    }

    for (String arg : args) {
      if (NO_SPLASH.equals(arg)) {
        System.setProperty(NO_SPLASH, "true");
        return;
      }
    }

    // must be out of activity measurement
    ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
    assert SPLASH_WINDOW == null;
    Activity activity = ParallelActivity.PREPARE_APP_INIT.start(ActivitySubNames.INITIALIZE_SPLASH);
    SPLASH_WINDOW = new Splash(appInfo);
    activity.end();
  }

  public static void executeWithHiddenSplash(@NotNull Window window, @NotNull Runnable runnable) {
    WindowAdapter listener = new WindowAdapter() {
      @Override
      public void windowOpened(WindowEvent e) {
        setVisible(false);
      }
    };
    window.addWindowListener(listener);

    runnable.run();

    setVisible(true);
    window.removeWindowListener(listener);
  }

  private static void setVisible(boolean value) {
    Splash splash = SPLASH_WINDOW;
    if (splash != null) {
      splash.setVisible(value);
      if (value) {
        splash.paint(splash.getGraphics());
      }
    }
  }

  public static void showLicenseeInfoOnSplash(@NotNull Logger log) {
    Splash splash = SPLASH_WINDOW;
    if (splash != null) {
      splash.paintLicenseeInfo();
      return;
    }

    SplashScreen javaSplash;
    try {
      javaSplash = SplashScreen.getSplashScreen();
    }
    catch (Throwable t) {
      log.warn(t);
      return;
    }

    if (javaSplash == null) {
      return;
    }

    ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
    if (Splash.showLicenseeInfo(javaSplash.createGraphics(), 0, 0, javaSplash.getSize().height, appInfo, Splash.createFont())) {
      javaSplash.update();
    }
  }

  @Nullable
  public static ProgressIndicator getProgressIndicator() {
    if (SPLASH_WINDOW == null) {
      return null;
    }

    return new EmptyProgressIndicator() {
      @Override
      public void setFraction(double fraction) {
        SPLASH_WINDOW.showProgress(fraction);
      }
    };
  }

  public static void hideBeforeShow(@NotNull Window window) {
    Runnable hideSplashTask = getHideTask();
    if (hideSplashTask != null) {
      window.addWindowListener(new WindowAdapter() {
        @Override
        public void windowOpened(WindowEvent e) {
          hideSplashTask.run();
          window.removeWindowListener(this);
        }
      });
    }
  }

  @Nullable
  public static Runnable getHideTask() {
    if (SPLASH_WINDOW == null) {
      return null;
    }

    Ref<Splash> splashRef = new Ref<>(SPLASH_WINDOW);
    SPLASH_WINDOW = null;

    return () -> {
      final Splash splash = splashRef.get();
      if (splash != null) {
        splashRef.set(null);
        splash.setVisible(false);
        splash.dispose();
      }
    };
  }
}
