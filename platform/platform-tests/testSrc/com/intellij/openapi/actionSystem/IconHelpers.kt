// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.icons.AllIcons
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.ui.JBColor
import com.intellij.util.ui.GrayFilter
import java.awt.Component
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.UIManager
import kotlin.math.abs

/** Paints the icon at its own size into a transparent ARGB image. */
internal fun Icon.paintToImage(component: Component = JPanel()): BufferedImage {
  val image = BufferedImage(iconWidth, iconHeight, BufferedImage.TYPE_INT_ARGB)
  val g = image.createGraphics()
  try {
    paintIcon(component, g, 0, 0)
  } finally {
    g.dispose()
  }
  return image
}

/** The ARGB pixels that are not fully transparent. */
internal fun BufferedImage.visiblePixels(): List<Int> = getRGB(0, 0, width, height, null, 0, width).filter { alphaOf(it) != 0 }

/** True when at least one pixel is not fully transparent. */
internal fun BufferedImage.hasVisiblePixels(): Boolean = visiblePixels().isNotEmpty()

/** The alpha byte of an ARGB pixel, 0 (transparent) to 255 (opaque). */
internal fun alphaOf(argb: Int): Int = argb ushr 24

/** The highest alpha of the visible pixels, otherwise throws when no pixel is visible. */
internal fun BufferedImage.maxAlpha(): Int = visiblePixels().maxOf { alphaOf(it) }

/** True when the red, green and blue channels differ by at most 2, which allows for rounding. */
internal fun isGray(argb: Int): Boolean {
  val r = argb shr 16 and 0xff
  val g = argb shr 8 and 0xff
  val b = argb and 0xff
  return abs(r - g) <= 2 && abs(g - b) <= 2 && abs(r - b) <= 2
}

/** Receives an action to be executed in dark theme environment. */
internal fun <T> withDarkTheme(action: () -> T): T {
  val wasBright = JBColor.isBright()
  JBColor.setDark(true)
  try {
    return withGrayFilter(GrayFilter(-70, -70, 100), action)
  } finally {
    JBColor.setDark(!wasBright)
  }
}

/** Runs [action] while the look and feel has [filter] as its `grayFilter`, which [com.intellij.util.ui.UIUtil.getGrayFilter] reads. */
internal fun <T> withGrayFilter(filter: GrayFilter, action: () -> T): T {
  val old = UIManager.get("grayFilter")
  UIManager.put("grayFilter", filter)
  try {
    return action()
  } finally {
    UIManager.put("grayFilter", old)
  }
}

/** The pixels of the Unknown marker. An action with only a legacy icon must paint exactly these. */
internal fun unknownMarkerPixels(): List<Int> {
  val marker = imageIconDescriptor("expui/toolbar/unknown.svg", AllIcons::class.java.classLoader)
  val pixels = marker.createSwingIcon().paintToImage().visiblePixels()
  check(pixels.isNotEmpty()) { "the unknown marker painted nothing" }
  return pixels
}
