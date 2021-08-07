// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import com.intellij.openapi.util.Couple
import com.intellij.ui.JBColor
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.ImageUtil.applyQualityRenderingHints
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import kotlin.math.abs
import kotlin.math.min

object AvatarUtils {
  fun createRoundRectIcon(image: BufferedImage, targetSize: Int): ImageIcon {
    val size: Int = min(image.width, image.height)
    val baseArcSize = 6.0 * size / targetSize

    val rounded = ImageUtil.createRoundedImage(image, baseArcSize)
    val hiDpi = ImageUtil.ensureHiDPI(rounded, ScaleContext.create())
    return JBImageIcon(ImageUtil.scaleImage(hiDpi, targetSize, targetSize))
  }

  fun generateColoredAvatar(gradientSeed: String, name: String, palette: ColorPalette = AvatarPalette): BufferedImage {
    val (color1, color2) = palette.gradient(gradientSeed)

    val shortName = Avatars.initials(name)
    val size = 64
    val image = ImageUtil.createImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g2 = image.createGraphics()
    applyQualityRenderingHints(g2)
    g2.paint = GradientPaint(0.0f, 0.0f, color2,
                             size.toFloat(), size.toFloat(), color1)
    g2.fillRect(0, 0, size, size)
    g2.paint = JBColor.WHITE
    g2.font = JBFont.create(Font("Segoe UI", Font.PLAIN, (size / 2.2).toInt()))
    UIUtil.drawCenteredString(g2, Rectangle(0, 0, size, size), shortName)
    g2.dispose()

    return image
  }
}

internal object Avatars {
  // "John Smith" -> "JS"
  fun initials(text: String): String {
    val words = text
      .filter { !it.isHighSurrogate() && !it.isLowSurrogate() }
      .trim()
      .split(' ', ',', '`', '\'', '\"').filter { it.isNotBlank() }
      .let {
        if (it.size > 2) listOf(it.first(), it.last()) else it
      }
      .take(2)
    if (words.size == 1) {
      return generateFromCamelCase(words.first())
    }
    return words.map { it.first() }
      .joinToString("").toUpperCase()
  }

  private fun generateFromCamelCase(text: String) = text.filterIndexed { index, c -> index == 0 || c.isUpperCase() }.take(2).toUpperCase()

  fun initials(firstName: String, lastName: String): String {
    return listOf(firstName, lastName).joinToString("") { it.first().toString() }
  }
}

abstract class ColorPalette {

  abstract val gradients: Array<Pair<Color, Color>>

  public fun gradient(seed: String? = null): Pair<Color, Color> {
    val keyCode = if (seed != null) {
      abs(seed.hashCode()) % gradients.size
    }
    else 0
    return gradients[keyCode]
  }
}

object AvatarPalette : ColorPalette() {

  override val gradients: Array<Pair<Color, Color>>
    get() = arrayOf(
      Color(0x60A800) to Color(0xD5CA00),
      Color(0x0A81F6) to Color(0x0A81F6),
      Color(0xAB3AF2) to Color(0xE40568),
      Color(0x21D370) to Color(0x03E9E1),
      Color(0x765AF8) to Color(0x5A91F8),
      Color(0x9F2AFF) to Color(0xE9A80B),
      Color(0x3BA1FF) to Color(0x36E97D),
      Color(0x9E54FF) to Color(0x0ACFF6),
      Color(0xD50F6B) to Color(0xE73AE8),
      Color(0x00C243) to Color(0x00FFFF),
      Color(0xB345F1) to Color(0x669DFF),
      Color(0xED5502) to Color(0xE73AE8),
      Color(0x4BE098) to Color(0x627FFF),
      Color(0x765AF8) to Color(0xC059EE),
      Color(0xED358C) to Color(0xDBED18),
      Color(0x168BFA) to Color(0x26F7C7),
      Color(0x9039D0) to Color(0xC239D0),
      Color(0xED358C) to Color(0xF9902E),
      Color(0x9D4CFF) to Color(0x39D3C3),
      Color(0x9F2AFF) to Color(0xFD56FD),
      Color(0xFF7500) to Color(0xFFCA00)
    )
}