// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.drawImage
import java.awt.*
import java.awt.image.BufferedImage

/**
 * To customize your IDE splash go to YourIdeNameApplicationInfo.xml and edit 'logo' tag. For more information, see documentation for
 * the tag attributes in ApplicationInfo.xsd file.
 */
internal class Splash(private val image: BufferedImage) :
  Dialog(null as Frame?, "splash" /* not visible, but available through window properties on Linux */) {
  init {
    isUndecorated = true
    background = Gray.TRANSPARENT
    // we don't hide splash when project frame is shown - show on top of,
    // it allows us to avoid focus issues (we do not focus the splash window)
    isAlwaysOnTop = true
    // makes tiling window managers on a Linux show window as floating
    isResizable = false
    focusableWindowState = false
    val size = Dimension(image.width, image.height)
    this.size = size

    val graphicsConfiguration = graphicsConfiguration
    val bounds = graphicsConfiguration.bounds
    if (SystemInfoRt.isWindows) {
      val insets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration)
      if (insets != null) {
        bounds.x += insets.left
        bounds.y += insets.top
        bounds.width -= insets.left + insets.right
        bounds.height -= insets.top + insets.bottom
      }
    }
    location = StartupUiUtil.getCenterPoint(bounds, size)
  }

  override fun paint(g: Graphics) {
    drawImage(g = g, image = image, x = 0, y = 0)
    FUSProjectHotStartUpMeasurer.splashBecameVisible()
  }
}