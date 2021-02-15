// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import com.intellij.ui.JBColor
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.Avatars.gradientInt
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

  fun generateColoredAvatar(gradientSeed: String, name: String): BufferedImage {
    val (colorInt1, colorInt2) = gradientInt(gradientSeed)
    val (color1, color2) = Color(colorInt1) to Color(colorInt2)

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

private object ColorPalette {
  val gradients = arrayOf(
    "#60A800" to "#D5CA00",
    "#0A81F6" to "#0A81F6",
    "#AB3AF2" to "#E40568",
    "#21D370" to "#03E9E1",
    "#765AF8" to "#5A91F8",
    "#9F2AFF" to "#E9A80B",
    "#3BA1FF" to "#36E97D",
    "#9E54FF" to "#0ACFF6",
    "#D50F6B" to "#E73AE8",
    "#00C243" to "#00FFFF",
    "#B345F1" to "#669DFF",
    "#ED5502" to "#E73AE8",
    "#4BE098" to "#627FFF",
    "#765AF8" to "#C059EE",
    "#ED358C" to "#DBED18",
    "#168BFA" to "#26F7C7",
    "#9039D0" to "#C239D0",
    "#ED358C" to "#F9902E",
    "#9D4CFF" to "#39D3C3",
    "#9F2AFF" to "#FD56FD",
    "#FF7500" to "#FFCA00"
  )
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

  fun gradient(seed: String? = null): Pair<String, String> {
    val keyCode = if (seed != null) {
      abs(seed.hashCode()) % ColorPalette.gradients.size
    }
    else {
      0
    }
    return ColorPalette.gradients[keyCode]
  }

  fun gradientInt(seed: String): Pair<Int, Int> {
    val gradientStrings = gradient(seed)
    val start = colorToInt(gradientStrings.first)
    val end = colorToInt(gradientStrings.second)
    return start to end
  }

  private fun colorToInt(color: String): Int {
    return 0xFF000000.toInt() or color.substring(1).toInt(16)
  }
}