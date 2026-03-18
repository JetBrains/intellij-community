// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.JBColor
import com.intellij.ui.components.Badge.ColorType
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBFont
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import javax.swing.Icon


/**
 * A pill-shaped badge [Icon] that renders [text] over a colored background.
 *
 * Use the predefined fields [new], [alpha], [beta], [trial]
 * for common badge types, or construct directly with a custom [text] and [colorType].
 *
 * Colors are resolved from `Badge.*` theme keys (see `IntelliJPlatform.themeMetadata.json`).
 * When [disabled] is `true`, the badge uses `Badge.disabledBackground` / `Badge.disabledForeground`
 * regardless of [colorType].
 *
 * @param text the localized label displayed inside the badge
 * @param colorType the color variant to use (default: [ColorType.BLUE_SECONDARY])
 */
@ApiStatus.Internal
class Badge(
  var text: @NlsContexts.Label String,
  var colorType: ColorType = ColorType.BLUE_SECONDARY,
) : Icon {

  companion object {
    @JvmStatic
    val new: Icon = Badge(IdeBundle.message("badge.text.new"), ColorType.BLUE)

    @JvmStatic
    val alpha: Icon = Badge(IdeBundle.message("badge.text.alpha"), ColorType.GREEN_SECONDARY)

    @JvmStatic
    val beta: Icon = Badge(IdeBundle.message("badge.text.beta"), ColorType.PURPLE_SECONDARY)

    @JvmStatic
    val trial: Icon = Badge(IdeBundle.message("badge.text.trial"), ColorType.GREEN_SECONDARY)
  }

  /**
   * Defines the color variant for a [Badge].
   *
   * Each type maps to a pair of background/foreground theme colors
   * resolved via `Badge.*` UI keys (e.g. `Badge.blueBackground`).
   */
  @ApiStatus.Internal
  enum class ColorType {
    BLUE,
    BLUE_SECONDARY,
    GREEN,
    GREEN_SECONDARY,
    PURPLE_SECONDARY,
    GRAY_SECONDARY,
  }

  /**
   * Whether to render the badge in its disabled (gray) appearance
   */
  var disabled: Boolean = false

  private val hPad: Int
    get() = JBUIScale.scale(6)

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    val g2 = g.create() as Graphics2D
    try {
      DarculaNewUIUtil.setupRenderingHints(g2)
      g2.translate(x, y)

      val width = iconWidth
      val height = iconHeight
      val arc = height / 2f

      val colorPair = if (disabled) disabledColorPair() else enabledColorPair(colorType)

      DarculaNewUIUtil.fillRoundedRectangle(g2, Rectangle(0, 0, width, height), colorPair.background, arc * 2)

      g2.color = colorPair.foreground
      g2.font = getTextFont()
      val fm = g2.fontMetrics
      val textY = (height + fm.ascent - fm.descent) / 2
      g2.drawString(text, hPad, textY)
    }
    finally {
      g2.dispose()
    }
  }

  private fun getTextFont(): Font {
    /* WEIGHT_MEDIUM doesn't work. Needed another solution or load Inter-SemiBold.otf
    val font = JBFont.small()
    val attributes = font.attributes + mapOf(TextAttribute.WEIGHT to TextAttribute.WEIGHT_MEDIUM)
    return font.deriveFont(attributes)
    */

    return JBFont.small()
  }

  override fun getIconWidth(): Int {
    val frc = FontRenderContext(AffineTransform(), true, true)
    val textBounds = getTextFont().getStringBounds(text, frc)
    return textBounds.width.toInt() + hPad * 2
  }

  override fun getIconHeight(): Int = JBUIScale.scale(16)
}

private data class ColorPair(
  val foreground: Color,
  val background: Color,
)

private fun enabledColorPair(colorType: ColorType): ColorPair {
  return when (colorType) {
    ColorType.BLUE -> ColorPair(
      JBColor.namedColor("Badge.blueForeground", JBColor(0xFFFFFF, 0x212326)),
      JBColor.namedColor("Badge.blueBackground", JBColor(0x3871E1, 0x538AF9)),
    )

    ColorType.BLUE_SECONDARY -> ColorPair(
      JBColor.namedColor("Badge.blueSecondaryForeground", JBColor(0x2F5EB9, 0xD0DFFE)),
      JBColor.namedColor("Badge.blueSecondaryBackground", JBColor(Color(0x3871E129, true), Color(0x2E4D89CC, true))),
    )

    ColorType.GREEN -> ColorPair(
      JBColor.namedColor("Badge.greenForeground", JBColor(0xFFFFFF, 0x212326)),
      JBColor.namedColor("Badge.greenBackground", JBColor(0x338555, 0x4E9D6C)),
    )

    ColorType.GREEN_SECONDARY -> ColorPair(
      JBColor.namedColor("Badge.greenSecondaryForeground", JBColor(0x2A6E47, 0xCDE5D1)),
      JBColor.namedColor("Badge.greenSecondaryBackground", JBColor(Color(0x33855529, true), Color(0x29583CCC, true))),
    )

    ColorType.PURPLE_SECONDARY -> ColorPair(
      JBColor.namedColor("Badge.purpleSecondaryForeground", JBColor(0x6C4EBB, 0xE2DBFC)),
      JBColor.namedColor("Badge.purpleSecondaryBackground", JBColor(Color(0x8060DB29.toInt(), true), Color(0x574092CC, true))),
    )

    ColorType.GRAY_SECONDARY -> ColorPair(
      JBColor.namedColor("Badge.graySecondaryForeground", JBColor(0x73767C, 0xB5B7BD)),
      JBColor.namedColor("Badge.graySecondaryBackground", JBColor(Color(0x73767C1F, true), Color(0xB5B7BD33.toInt(), true))),
    )
  }
}

private fun disabledColorPair(): ColorPair {
  return ColorPair(
    JBColor.namedColor("Badge.disabledForeground", JBColor(0xB5B7BD, 0x8B8E94)),
    JBColor.namedColor("Badge.disabledBackground", JBColor(Color(0x73767C1F, true), Color(0xB5B7BD33.toInt(), true)))
  )
}
