// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.RegistryManager;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseWheelEvent;

/**
 * Converts a Swing wheel event to the wheel rotation of the event that {@link JBCefOsrComponent} sends to CEF.
 * <p>
 * For {@link MouseWheelEvent#WHEEL_UNIT_SCROLL}, the native JCEF code sends {@code scrollAmount * wheelRotation} to CEF.
 * On Windows, CEF divides this value by {@code WHEEL_DELTA} and multiplies it by the OS lines per notch.
 * On macOS, CEF uses this value as pixels.
 * On other platforms and for other scroll types, the registry factor applies.
 */
final class JBCefOsrWheelRotation {
  private static final int WINDOWS_WHEEL_DELTA = 120;
  private static final int MAC_PIXELS_PER_UNIT = 10;

  private final int myRegistryFactor = RegistryManager.getInstance().intValue("ide.browser.jcef.osr.wheelRotation.factor");
  private double myRemainder;
  private boolean myWasHorizontal;

  int getFactor(@NotNull MouseWheelEvent e) {
    if (!usesPlatformFactor(e)) return myRegistryFactor;
    return SystemInfoRt.isWindows ? WINDOWS_WHEEL_DELTA : MAC_PIXELS_PER_UNIT;
  }

  int getScrollAmount(@NotNull MouseWheelEvent e) {
    if (e.getScrollType() != MouseWheelEvent.WHEEL_UNIT_SCROLL || usesPlatformFactor(e)) return 1;
    return e.getScrollAmount();
  }

  /**
   * Returns the integer part of the accumulated rotation and keeps the fractional part for the next event.
   */
  int accumulate(double rotation, boolean isHorizontal) {
    if (isHorizontal != myWasHorizontal || Math.signum(rotation) != Math.signum(myRemainder)) {
      myWasHorizontal = isHorizontal;
      myRemainder = 0;
    }
    double total = myRemainder + rotation;
    int result = (int)total;
    myRemainder = total - result;
    return result;
  }

  private static boolean usesPlatformFactor(@NotNull MouseWheelEvent e) {
    return e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL && (SystemInfoRt.isWindows || SystemInfoRt.isMac);
  }
}
