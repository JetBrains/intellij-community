// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleType;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import sun.java2d.SunGraphicsEnvironment;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

public final class JreHiDpiUtil {
  // accessed from com.intellij.util.ui.TestScaleHelper via reflect
  private static final AtomicReference<Boolean> jreHiDPI = new AtomicReference<>();
  private static volatile boolean jreHiDPI_earlierVersion;

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the graphics configuration represents a HiDPI device.
   * (analogue of {@link UIUtil#isRetina(Graphics2D)} on macOS)
   */
  public static boolean isJreHiDPI(@Nullable GraphicsConfiguration gc) {
    return isJreHiDPIEnabled() && JBUIScale.isHiDPI(JBUIScale.sysScale(gc));
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the graphics represents a HiDPI device.
   * (analogue of {@link UIUtil#isRetina(Graphics2D)} on macOS)
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
    if (jreHiDPI.get() != null) return jreHiDPI.get();

    synchronized (jreHiDPI) {
      if (jreHiDPI.get() != null) return jreHiDPI.get();

      jreHiDPI.set(false);
      if (!SystemProperties.getBooleanProperty("hidpi", true)) {
        return false;
      }
      jreHiDPI_earlierVersion = true;
      if (SystemInfo.isJetBrainsJvm) {
        try {
          GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
          if (ge instanceof SunGraphicsEnvironment) {
            Method m = ReflectionUtil.getDeclaredMethod(SunGraphicsEnvironment.class, "isUIScaleEnabled");
            jreHiDPI.set(m != null && (Boolean)m.invoke(ge));
            jreHiDPI_earlierVersion = false;
          }
        }
        catch (Throwable ignore) {
        }
      }
      if (SystemInfo.isMac) {
        jreHiDPI.set(true);
      }
      return jreHiDPI.get();
    }
  }

  /**
   * Indicates earlier JBSDK version, not containing HiDPI changes.
   * On macOS such JBSDK supports jreHiDPI, but it's not capable to provide device scale
   * via GraphicsDevice transform matrix (the scale should be retrieved via DetectRetinaKit).
   */
  @ApiStatus.Internal
  public static boolean isJreHiDPI_earlierVersion() {
    isJreHiDPIEnabled();
    return jreHiDPI_earlierVersion;
  }

  @TestOnly
  @NotNull
  public static AtomicReference<Boolean> test_jreHiDPI() {
    if (jreHiDPI.get() == null) {
      // force init
      isJreHiDPIEnabled();
    }
    return jreHiDPI;
  }
}
