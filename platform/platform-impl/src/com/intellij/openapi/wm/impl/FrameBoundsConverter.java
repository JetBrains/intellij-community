// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.scale.JBUIScale;
import kotlin.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Converts the frame bounds b/w the user space (JRE-managed HiDPI mode) and the device space (IDE-managed HiDPI mode).
 * See {@link JreHiDpiUtil#isJreHiDPIEnabled()}
 */
@ApiStatus.Internal
public final class FrameBoundsConverter {
  /**
   * @param bounds the bounds in the device space
   * @return the bounds in the user space
   */
  public static @NotNull Pair<@NotNull Rectangle, @Nullable GraphicsDevice> convertFromDeviceSpaceAndFitToScreen(@NotNull Rectangle bounds) {
    Rectangle b = bounds.getBounds();
    int centerX = b.x + b.width / 2;
    int centerY = b.y + b.height / 2;
    boolean scaleNeeded = shouldConvert();
    for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
      GraphicsConfiguration gc = gd.getDefaultConfiguration();
      Rectangle devBounds = gc.getBounds(); // in user space
      if (scaleNeeded) {
        scaleUp(devBounds, gc); // to device space if needed
      }
      if (devBounds.contains(centerX, centerY)) {
        if (scaleNeeded) {
          scaleDown(b, gc); // to user space if needed
        }
        // do not return bounds bigger than the corresponding screen rectangle
        Rectangle screen = ScreenUtil.getScreenRectangle(gc);
        if (b.x < screen.x) {
          b.x = screen.x;
        }
        if (b.y < screen.y) {
          b.y = screen.y;
        }
        if (b.width > screen.width) {
          b.width = screen.width;
        }
        if (b.height > screen.height) {
          b.height = screen.height;
        }
        return new Pair<>(bounds, gd);
      }
    }
    // we didn't find proper device at all, probably it was an external screen that is unavailable now, we cannot use specified bounds
    ScreenUtil.fitToScreen(b);
    return new Pair<>(bounds, null);
  }

  /**
   * @param gc the graphics config
   * @param bounds the bounds in the user space
   * @return the bounds in the device space
   */
  public static Rectangle convertToDeviceSpace(GraphicsConfiguration gc, @NotNull Rectangle bounds) {
    Rectangle b = bounds.getBounds();
    if (!shouldConvert()) return b;

    try {
      scaleUp(b, gc);
    }
    catch (HeadlessException ignore) {
    }
    return b;
  }

  private static boolean shouldConvert() {
    if (SystemInfoRt.isLinux || // JRE-managed HiDPI mode is not yet implemented (pending)
        SystemInfoRt.isMac)     // JRE-managed HiDPI mode is permanent
    {
      return false;
    }
    // device space equals user space
    return JreHiDpiUtil.isJreHiDPIEnabled();
  }

  private static void scaleUp(@NotNull Rectangle bounds, @NotNull GraphicsConfiguration gc) {
    scale(bounds, gc.getBounds(), JBUIScale.sysScale(gc));
  }

  private static void scaleDown(@NotNull Rectangle bounds, @NotNull GraphicsConfiguration gc) {
    float scale = JBUIScale.sysScale(gc);
    assert scale != 0;
    scale(bounds, gc.getBounds(), 1 / scale);
  }

  private static void scale(@NotNull Rectangle bounds, @NotNull Rectangle deviceBounds, float scale) {
    // On Windows, JB SDK transforms the screen bounds to the user space as follows:
    // [x, y, width, height] -> [x, y, width / scale, height / scale]
    // xy are not transformed in order to avoid overlapping of the screen bounds in multi-dpi env.

    // scale the delta b/w xy and deviceBounds.xy
    int x = (int)Math.floor(deviceBounds.x + (bounds.x - deviceBounds.x) * scale);
    int y = (int)Math.floor(deviceBounds.y + (bounds.y - deviceBounds.y) * scale);

    bounds.setBounds(x, y, (int)Math.ceil(bounds.width * scale), (int)Math.ceil(bounds.height * scale));
  }
}
