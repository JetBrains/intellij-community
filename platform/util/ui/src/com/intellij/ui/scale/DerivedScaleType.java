// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale;

import java.awt.*;

/**
 * The scale factors derived from the {@link ScaleType} scale factors. Used for convenience.
 */
public enum DerivedScaleType {
  /**
   * The effective user scale factor "combines" all the user space scale factors which are: {@code USR_SCALE} and {@code OBJ_SCALE}.
   * So, basically it equals {@code USR_SCALE} * {@code OBJ_SCALE}.
   */
  EFF_USR_SCALE,
  /**
   * The device scale factor. In JRE-HiDPI mode equals {@link ScaleType#SYS_SCALE}, in IDE-HiDPI mode equals 1.0
   * (in IDE-HiDPI the user space and the device space are equal and so the transform b/w the spaces is 1.0)
   */
  DEV_SCALE,
  /**
   * The pixel scale factor "combines" all the other scale factors (user, system and object) and defines the
   * effective scale of a particular UI object.
   *
   * For instance, on Mac Retina monitor (JRE-managed HiDPI) in the Presentation mode (which, say,
   * doubles the UI scale) the pixel scale would equal 4.0 (provided the object scale is 1.0). The value
   * is the product of the user scale 2.0 and the system scale 2.0. In the IDE-managed HiDPI mode,
   * the pixel scale equals {@link #EFF_USR_SCALE}.
   *
   * @see JBUIScale#pixScale()
   * @see JBUIScale#pixScale(GraphicsConfiguration)
   * @see JBUIScale#pixScale(Graphics2D)
   * @see JBUIScale#pixScale(Component)
   * @see JBUIScale#pixScale(float)
   */
  PIX_SCALE
}
