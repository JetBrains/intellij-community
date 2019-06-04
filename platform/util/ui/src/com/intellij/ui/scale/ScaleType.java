// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.ImageObserver;

/**
 * The IDE supports two different HiDPI modes:
 *
 * 1) IDE-managed HiDPI mode.
 *
 * Supported for backward compatibility until complete transition to the JRE-managed HiDPI mode happens.
 * In this mode there's a single coordinate space and the whole UI is scaled by the IDE guided by the
 * user scale factor ({@link #USR_SCALE}).
 *
 * 2) JRE-managed HiDPI mode.
 *
 * In this mode the JRE scales graphics prior to drawing it on the device. So, there're two coordinate
 * spaces: the user space and the device space. The system scale factor ({@link #SYS_SCALE}) defines the
 * transform b/w the spaces. The UI size metrics (windows, controls, fonts height) are in the user
 * coordinate space. Though, the raster images should be aware of the device scale in order to meet
 * HiDPI. (For instance, JRE on a Mac Retina monitor device works in the JRE-managed HiDPI mode,
 * transforming graphics to the double-scaled device coordinate space)
 *
 * The IDE operates the scale factors of the following types:
 *
 * 1) The user scale factor: {@link #USR_SCALE}
 * 2) The system (monitor device) scale factor: {@link #SYS_SCALE}
 * 3) The object (UI instance specific) scale factor: {@link #OBJ_SCALE}
 *
 * @see com.intellij.ui.JreHiDpiUtil#isJreHiDPIEnabled()
 * @see com.intellij.util.ui.UIUtil#isJreHiDPI()
 * @see com.intellij.util.ui.UIUtil#isJreHiDPI(GraphicsConfiguration)
 * @see com.intellij.util.ui.UIUtil#isJreHiDPI(Graphics2D)
 * @see JBUIScale#isUsrHiDPI()
 * @see com.intellij.util.ui.UIUtil#drawImage(Graphics, Image, Rectangle, Rectangle, ImageObserver)
 * @see com.intellij.util.ui.UIUtil#createImage(Graphics, int, int, int)
 * @see com.intellij.util.ui.UIUtil#createImage(GraphicsConfiguration, int, int, int)
 * @see com.intellij.util.ui.UIUtil#createImage(int, int, int)
 * @see ScaleContext
 */
public enum ScaleType {
  /**
   * The user scale factor is set and managed by the IDE. Currently it's derived from the UI font size,
   * specified in the IDE Settings.
   *
   * The user scale value depends on which HiDPI mode is enabled. In the IDE-managed HiDPI mode the
   * user scale "includes" the default system scale and simply equals it with the default UI font size.
   * In the JRE-managed HiDPI mode the user scale is independent of the system scale and equals 1.0
   * with the default UI font size. In case the default UI font size changes, the user scale changes
   * proportionally in both the HiDPI modes.
   *
   * In the IDE-managed HiDPI mode the user scale completely defines the UI scale. In the JRE-managed
   * HiDPI mode the user scale can be considered a supplementary scale taking effect in cases like
   * the IDE Presentation Mode and when the default UI scale is changed by the user.
   *
   * @see JBUIScale#setUserScaleFactor(float)
   * @see JBUIScale#scale(float)
   * @see JBUIScale#scale(int)
   */
  USR_SCALE,
  /**
   * The system scale factor is defined by the device DPI and/or the system settings. For instance,
   * Mac Retina monitor device has the system scale 2.0 by default. As there can be multiple devices
   * (multi-monitor configuration) there can be multiple system scale factors, appropriately. However,
   * there's always a single default system scale factor corresponding to the default device. And it's
   * the only system scale available in the IDE-managed HiDPI mode.
   *
   * In the JRE-managed HiDPI mode, the system scale defines the scale of the transform b/w the user
   * and the device coordinate spaces performed by the JRE.
   *
   * @see JBUIScale#sysScale()
   * @see JBUIScale#sysScale(GraphicsConfiguration)
   * @see JBUIScale#sysScale(Graphics2D)
   * @see JBUIScale#sysScale(Component)
   */
  SYS_SCALE,
  /**
   * An extra scale factor of a particular UI object, which doesn't affect any other UI object, as opposed
   * to the user scale and the system scale factors. This scale factor affects the user space size of the object
   * and doesn't depend on the HiDPI mode. By default it is set to 1.0.
   */
  OBJ_SCALE;

  @NotNull
  public Scale of(double value) {
    return Scale.create(value, this);
  }
}
