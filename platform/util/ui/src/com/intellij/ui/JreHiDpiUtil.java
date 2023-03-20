// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

public final class JreHiDpiUtil {
  private static final AtomicReference<Boolean> jreHiDPI = new AtomicReference<>();

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the graphics configuration represents a HiDPI device.
   * (analogue of {@link com.intellij.util.ui.UIUtil#isRetina(Graphics2D)} on macOS)
   */
  public static boolean isJreHiDPI(@Nullable GraphicsConfiguration gc) {
    return isJreHiDPI(JBUIScale.sysScale(gc));
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the specified scaling level corresponds to a HiDPI device.
   * (analogue of {@link com.intellij.util.ui.UIUtil#isRetina(Graphics2D)} on macOS)
   */
  public static boolean isJreHiDPI(float sysScale) {
    return isJreHiDPIEnabled() && JBUIScale.isHiDPI(sysScale);
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the graphics represents a HiDPI device.
   * (analogue of {@link com.intellij.util.ui.UIUtil#isRetina(Graphics2D)} on macOS)
   */
  public static boolean isJreHiDPI(@Nullable Graphics2D g) {
    return isJreHiDPIEnabled() && JBUIScale.isHiDPI(JBUIScale.sysScale(g));
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled.
   * (True for macOS JDK >= 7.10 versions)
   *
   * @see ScaleType
   */
  public static boolean isJreHiDPIEnabled() {
    if (SystemInfoRt.isMac) {
      return true;
    }

    Boolean value = jreHiDPI.get();
    if (value != null) {
      return value;
    }

    synchronized (jreHiDPI) {
      value = jreHiDPI.get();
      if (value != null) {
        return value;
      }

      value = false;
      if (Boolean.parseBoolean(System.getProperty("hidpi", "true")) && SystemInfo.isJetBrainsJvm) {
        try {
          GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
          Class<?> sunGraphicsEnvironmentClass = Class.forName("sun.java2d.SunGraphicsEnvironment");
          if (sunGraphicsEnvironmentClass.isInstance(ge)) {
            try {
              Method method = sunGraphicsEnvironmentClass.getDeclaredMethod("isUIScaleEnabled");
              method.setAccessible(true);
              value = (Boolean)method.invoke(ge);
            }
            catch (NoSuchMethodException e) {
              value = false;
            }
          }
        }
        catch (Throwable ignore) {
        }
      }
      jreHiDPI.set(value);
    }
    return value;
  }

  @TestOnly
  @NotNull
  public static AtomicReference<Boolean> test_jreHiDPI() {
    isJreHiDPIEnabled(); // force init
    return jreHiDPI;
  }
}
