// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

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

    assert SPLASH_WINDOW == null;
    SPLASH_WINDOW = new Splash(ApplicationInfoImpl.getShadowInstance());
  }

  public static void setVisible(boolean value) {
    Splash splash = SPLASH_WINDOW;
    if (splash != null) {
      splash.setVisible(value);
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

  @Nullable
  public static Runnable getHideTask() {
    if (SPLASH_WINDOW == null) {
      return null;
    }

    Ref<Splash> splash = new Ref<>(SPLASH_WINDOW);
    SPLASH_WINDOW = null;
    return () -> {
      splash.get().setVisible(false);
      splash.get().dispose();
      splash.set(null);
    };
  }
}
