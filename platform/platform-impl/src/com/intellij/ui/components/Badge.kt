// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBFont
import com.intellij.ide.IdeBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.swing.Icon

/**
 * Defines the color variant for a [Badge].
 *
 * Each type maps to a pair of background/foreground theme colors
 * resolved via `Badge.*` UI keys (e.g. `Badge.blueBackground`).
 */
@ApiStatus.Experimental
enum class BadgeColorType {
  BLUE_SECONDARY,
  BLUE,
  GREEN_SECONDARY,
  GREEN,
  PURPLE_SECONDARY,
  GRAY_SECONDARY,
}

/**
 * A pill-shaped badge [Icon] that renders [text] over a colored background.
 *
 * Use the predefined factory methods ([newBadge], [betaBadge], [freeBadge], [trialBadge])
 * for common badge types, or construct directly with a custom [text] and [colorType].
 *
 * Colors are resolved from `Badge.*` theme keys (see `IntelliJPlatform.themeMetadata.json`).
 * When [disabled] is `true`, the badge uses `Badge.disabledBackground` / `Badge.disabledForeground`
 * regardless of [colorType].
 *
 * @param text the localized label displayed inside the badge
 * @param colorType the color variant to use (default: [BadgeColorType.BLUE_SECONDARY])
 * @param disabled whether to render the badge in its disabled (gray) appearance
 */
@ApiStatus.Experimental
class Badge(
  val text: @Nls String,
  val colorType: BadgeColorType = BadgeColorType.BLUE_SECONDARY,
  val disabled: Boolean = false,
) : Icon {

  companion object {
    /** Creates a blue primary badge labeled "New". */
    @JvmStatic
    fun newBadge(): Badge = Badge(IdeBundle.message("badge.text.new"), BadgeColorType.BLUE)

    /** Creates a purple secondary badge labeled "Beta". */
    @JvmStatic
    fun betaBadge(): Badge = Badge(IdeBundle.message("badge.text.beta"), BadgeColorType.PURPLE_SECONDARY)

    /** Creates a green primary badge labeled "Free". */
    @JvmStatic
    fun freeBadge(): Badge = Badge(IdeBundle.message("badge.text.free"), BadgeColorType.GREEN)

    /** Creates a green secondary badge labeled "Trial". */
    @JvmStatic
    fun trialBadge(): Badge = Badge(IdeBundle.message("badge.text.trial"), BadgeColorType.GREEN_SECONDARY)
  }

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    val g2 = g.create() as Graphics2D
    try {
      DarculaNewUIUtil.setupRenderingHints(g2)
      g2.translate(x, y)

      val width = iconWidth
      val height = iconHeight
      val arc = height / 2f

      val bgColor = if (disabled) disabledBackgroundColor() else backgroundColorFor(colorType)
      val fgColor = if (disabled) disabledForegroundColor() else foregroundColorFor(colorType)

      DarculaNewUIUtil.fillRoundedRectangle(g2, Rectangle(0, 0, width, height), bgColor, arc * 2)

      g2.color = fgColor
      g2.font = JBFont.small()
      val fm = g2.fontMetrics
      val textX = JBUIScale.scale(6)
      val textY = (height + fm.ascent - fm.descent) / 2
      g2.drawString(text, textX, textY)
    }
    finally {
      g2.dispose()
    }
  }

  override fun getIconWidth(): Int {
    val fm = java.awt.Toolkit.getDefaultToolkit().let {
      val font = JBFont.small()
      @Suppress("DEPRECATION")
      it.getFontMetrics(font)
    }
    val textWidth = fm.stringWidth(text)
    val hPad = JBUIScale.scale(6)
    return textWidth + hPad * 2
  }

  override fun getIconHeight(): Int = JBUIScale.scale(16)
}

// ---- Color resolution (shared, internal) ----

@ApiStatus.Internal
internal fun backgroundColorFor(colorType: BadgeColorType): Color =
  JBColor.namedColor(backgroundKey(colorType), defaultBackground(colorType))

@ApiStatus.Internal
internal fun foregroundColorFor(colorType: BadgeColorType): Color =
  JBColor.namedColor(foregroundKey(colorType), defaultForeground(colorType))

@ApiStatus.Internal
internal fun disabledBackgroundColor(): Color =
  JBColor.namedColor("Badge.disabledBackground", JBColor(Color(0x73767C1F, true), Color(0xB5B7BD33.toInt(), true)))

@ApiStatus.Internal
internal fun disabledForegroundColor(): Color =
  JBColor.namedColor("Badge.disabledForeground", JBColor(0xB5B7BD, 0x8B8E94))

private fun backgroundKey(colorType: BadgeColorType): String = when (colorType) {
  BadgeColorType.BLUE_SECONDARY -> "Badge.blueSecondaryBackground"
  BadgeColorType.BLUE -> "Badge.blueBackground"
  BadgeColorType.GREEN_SECONDARY -> "Badge.greenSecondaryBackground"
  BadgeColorType.GREEN -> "Badge.greenBackground"
  BadgeColorType.PURPLE_SECONDARY -> "Badge.purpleSecondaryBackground"
  BadgeColorType.GRAY_SECONDARY -> "Badge.graySecondaryBackground"
}

private fun foregroundKey(colorType: BadgeColorType): String = when (colorType) {
  BadgeColorType.BLUE_SECONDARY -> "Badge.blueSecondaryForeground"
  BadgeColorType.BLUE -> "Badge.blueForeground"
  BadgeColorType.GREEN_SECONDARY -> "Badge.greenSecondaryForeground"
  BadgeColorType.GREEN -> "Badge.greenForeground"
  BadgeColorType.PURPLE_SECONDARY -> "Badge.purpleSecondaryForeground"
  BadgeColorType.GRAY_SECONDARY -> "Badge.graySecondaryForeground"
}

private fun defaultBackground(colorType: BadgeColorType): Color = when (colorType) {
  BadgeColorType.BLUE_SECONDARY -> JBColor(Color(0x3871E129, true), Color(0x2E4D89CC, true))
  BadgeColorType.BLUE -> JBColor(0x3871E1, 0x538AF9)
  BadgeColorType.GREEN_SECONDARY -> JBColor(Color(0x33855529, true), Color(0x29583CCC, true))
  BadgeColorType.GREEN -> JBColor(0x338555, 0x4E9D6C)
  BadgeColorType.PURPLE_SECONDARY -> JBColor(Color(0x8060DB29.toInt(), true), Color(0x574092CC, true))
  BadgeColorType.GRAY_SECONDARY -> JBColor(Color(0x73767C1F, true), Color(0xB5B7BD33.toInt(), true))
}

private fun defaultForeground(colorType: BadgeColorType): Color = when (colorType) {
  BadgeColorType.BLUE_SECONDARY -> JBColor(0x2F5EB9, 0xD0DFFE)
  BadgeColorType.BLUE -> JBColor(0xFFFFFF, 0x212326)
  BadgeColorType.GREEN_SECONDARY -> JBColor(0x2A6E47, 0xCDE5D1)
  BadgeColorType.GREEN -> JBColor(0xFFFFFF, 0x212326)
  BadgeColorType.PURPLE_SECONDARY -> JBColor(0x6C4EBB, 0xE2DBFC)
  BadgeColorType.GRAY_SECONDARY -> JBColor(0x73767C, 0xB5B7BD)
}
