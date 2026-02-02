// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.ui.NewUiValue
import com.intellij.ui.paint.withTxAndClipAligned
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.AvatarUtils.generateColoredAvatar
import com.intellij.util.ui.ImageUtil.applyQualityRenderingHints
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsConfiguration
import java.awt.Rectangle
import java.awt.font.TextAttribute
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.math.abs


class AvatarIcon(
  val targetSize: Int,
  val arcRatio: Double,
  val presentation: AvatarPresentation,
) : JBCachingScalableIcon<AvatarIcon>() {
  constructor(
    targetSize: Int,
    arcRatio: Double,
    gradientSeed: String,
    avatarName: String,
    palette: ColorPalette = AvatarPalette,
  ) : this(targetSize, arcRatio, PaletteAvatarPresentation(avatarName, gradientSeed, palette))

  private var cachedImage: BufferedImage? = null
  private var cachedImageSysScale: Float? = null
  private var cachedImagePixScale: Float? = null
  private var cachedImageColor: Color? = null

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    g as Graphics2D
    val iconSize = getIconSize()
    val sysScale = JBUIScale.sysScale(g)
    val pixScale = JBUI.pixScale(g.deviceConfiguration)
    val imageColor = presentation.color1
    if (sysScale != cachedImageSysScale
        || pixScale != cachedImagePixScale
        || imageColor != cachedImageColor) {
      cachedImage = null
    }

    var cachedImage = cachedImage
    if (cachedImage == null) {
      cachedImage = generateColoredAvatar(gc = g.deviceConfiguration,
                                          size = iconSize,
                                          arcRatio = arcRatio,
                                          presentation = presentation)
      this.cachedImage = cachedImage
      cachedImageSysScale = sysScale
      cachedImagePixScale = pixScale
      cachedImageColor = presentation.color1
    }

    withTxAndClipAligned(g, x, y, cachedImage.width, cachedImage.height) { gg ->
      StartupUiUtil.drawImage(gg, cachedImage)
    }
  }

  private fun getIconSize() = scaleVal(targetSize.toDouble()).toInt()

  override fun getIconWidth(): Int = getIconSize()

  override fun getIconHeight(): Int = getIconSize()

  override fun copy(): AvatarIcon {
    val copy = AvatarIcon(targetSize, arcRatio, presentation)
    copy.updateContextFrom(this)
    return copy
  }
}

object AvatarUtils {
  fun generateColoredAvatar(gradientSeed: String,
                            name: String,
                            size: Int = 64,
                            arcRatio: Double = 0.0,
                            palette: ColorPalette = AvatarPalette): BufferedImage {
    val presentation = PaletteAvatarPresentation(name, gradientSeed, palette)
    return generateColoredAvatar(null, size, arcRatio, presentation)
  }

  internal fun generateColoredAvatar(
    gc: GraphicsConfiguration?,
    size: Int,
    arcRatio: Double,
    presentation: AvatarPresentation,
  ): BufferedImage {
    val image = ImageUtil.createImage(gc, size, size, BufferedImage.TYPE_INT_ARGB)
    val g2 = image.createGraphics()
    applyQualityRenderingHints(g2)
    g2.paint = GradientPaint(0.0f, size.toFloat(), presentation.color2,
                             size.toFloat(), 0.0f, presentation.color1)

    val arcSize = arcRatio * size
    val avatarOvalArea = Area(RoundRectangle2D.Double(0.0, 0.0,
                                                      size.toDouble(), size.toDouble(),
                                                      arcSize, arcSize))
    g2.fill(avatarOvalArea)

    @Suppress("UseJBColor")
    g2.paint = Color.WHITE // JBColor.WHITE paints black color ¯\_(ツ)_/¯
    g2.font = getFont(size)
    UIUtil.drawCenteredString(g2, Rectangle(0, 0, size, size), presentation.shortName)
    g2.dispose()

    return image
  }

  private fun getFont(size: Int): Font {
    return if (NewUiValue.isEnabled()) {
      val fontSize = 13 * size / 20
      getNewUiFont(fontSize)
    }
    else {
      JBFont.create(Font("Segoe UI", Font.PLAIN, (size / 2.2).toInt()))
    }
  }

  private fun getNewUiFont(size: Int): Font {
    val attributes = mutableMapOf<TextAttribute, Any?>()

    attributes[TextAttribute.FAMILY] = "JetBrains Mono"
    attributes[TextAttribute.WEIGHT] = TextAttribute.WEIGHT_DEMIBOLD

    return JBFont.create(Font.getFont(attributes)).deriveFont(size.toFloat())
  }

  // "John Smith" -> "JS"
  // "John-Smith-Harris" -> "JH"
  // "MyProject" -> "MP"
  // "My-Project" -> "MP"
  @VisibleForTesting
  @Internal
  fun initials(text: String): String {
    val filtered = text
      .filter { !it.isHighSurrogate() && !it.isLowSurrogate() }
      .trim()

    val camelCaseInitials = generateFromCamelCase(text)
    if (camelCaseInitials.length == 2) return camelCaseInitials

    val words = (filtered.splitAtLeast2NonEmpty(' ')
                 ?: filtered.splitAtLeast2NonEmpty(',')
                 ?: filtered.splitAtLeast2NonEmpty('-')
                 ?: filtered.splitAtLeast2NonEmpty('_')
                 ?: filtered.splitAtLeast2NonEmpty('.')
                 ?: filtered.splitAtLeast2NonEmpty('`', '\'', '\"'))
      ?.let { listOf(it.first(), it.last()) }

    if (words == null) {
      return camelCaseInitials
    }
    return words.map { it.first() }
      .joinToString("").uppercase()
  }

  private fun String.splitAtLeast2NonEmpty(vararg delimiters: Char) = split(*delimiters).map { string ->
    string.filter { it.isLetterOrDigit() }
  }.filter {
    it.isNotEmpty()
  }.takeIf {
    it.size >= 2
  }

  private fun generateFromCamelCase(text: String): String {
    return text.dropWhile {
      !it.isLetter()
    }.takeWhile {
      it.isLetterOrDigit()
    }.filterIndexed { index, c ->
      index == 0 || c.isUpperCase()
    }.take(2).uppercase()
  }

  fun initials(firstName: String, lastName: String): String {
    return listOf(firstName, lastName).joinToString("") { it.first().toString() }
  }
}

interface ColorPalette {
  companion object {
    fun <T> select(storage: Array<T>, seed: String? = null): T {
      val keyCode = seed?.let { abs(it.hashCode()) % storage.size } ?: 0

      return storage[keyCode]
    }
  }
  val gradients: Array<Pair<Color, Color>>

  fun gradient(seed: String? = null): Pair<Color, Color> = select(gradients, seed)
}

@Internal
object AvatarPalette : ColorPalette {
  override val gradients: Array<Pair<Color, Color>>
    get() {
      return arrayOf(
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
}

interface AvatarPresentation {
  val shortName: String
  val color1: Color
  val color2: Color
}

data class PaletteAvatarPresentation(val name: String, val gradientSeed: String, val palette: ColorPalette) : AvatarPresentation {
  private val colors = palette.gradient(gradientSeed)

  override val shortName: String = AvatarUtils.initials(name)
  override val color1: Color get() = colors.first
  override val color2: Color get() = colors.second
}
