// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.intellij.openapi.wm.impl.WindowManagerImplKt.IDE_FRAME_EVENT_LOG;

/**
 * Converts the frame bounds b/w the user space (JRE-managed HiDPI mode) and the device space (IDE-managed HiDPI mode).
 * See {@link JreHiDpiUtil#isJreHiDPIEnabled()}
 */
@ApiStatus.Internal
public final class FrameBoundsConverter {
  static final int MIN_WIDTH = 350;
  static final int MIN_HEIGHT = 150;

  /**
   * @param bounds the bounds in the device space
   * @return the bounds in the user space
   */
  public static @Nullable Rectangle convertFromDeviceSpaceAndFitToScreen(@NotNull Rectangle bounds) {
    int tolerance = Registry.intValue("ide.project.frame.screen.bounds.tolerance", 10);
    Rectangle b = bounds.getBounds();
    // Protect against incorrectly saved meaningless values.
    if (b.width < MIN_WIDTH) b.width = MIN_WIDTH;
    if (b.height < MIN_HEIGHT) b.height = MIN_HEIGHT;
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
        // but allow for the invisible parts of the frame (border drag zones) to be placed slightly outside
        screen.x -= tolerance;
        screen.y -= tolerance;
        screen.width += tolerance;
        screen.height += tolerance;
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
        if (IDE_FRAME_EVENT_LOG.isDebugEnabled()) { // avoid unnecessary concatenation
          IDE_FRAME_EVENT_LOG.debug("Found the screen " + screen + " for the loaded bounds " + bounds);
        }
        return b;
      }
    }

    if (IDE_FRAME_EVENT_LOG.isDebugEnabled()) { // avoid unnecessary concatenation
      IDE_FRAME_EVENT_LOG.debug("Found no screen for the loaded bounds " + bounds);
    }
    // We didn't find a proper device at all. Probably it was an external screen that is unavailable now. We cannot use specified bounds.
    return null;
  }

  /**
   * @param gc the graphics config
   * @param bounds the bounds in the user space
   * @return the bounds in the device space
   */
  public static Rectangle convertToDeviceSpace(GraphicsConfiguration gc, @NotNull Rectangle bounds) {
    Rectangle b = bounds.getBounds();
    if (shouldConvert()) {
      try {
        scaleUp(b, gc);
      }
      catch (HeadlessException ignore) { }
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

  static void scaleDown(@NotNull Rectangle bounds, @NotNull GraphicsConfiguration gc) {
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
