// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.ui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.ColorUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.util.function.Supplier
import kotlin.math.abs

@ApiStatus.Internal
interface RepositoryColorGenerator<RepoID> {
  fun getColor(repoId: RepoID, colorSpace: String = RepositoryColors.DEFAULT_COLOR_SPACE): Color

  class AdditionalColorSpace(val colorKey: String, val colors: List<Color>)
}

@ApiStatus.Internal
object RepositoryColors {
  const val DEFAULT_COLOR_SPACE: String = "default"

  val defaultColor: Color
    get() = UIUtil.getTableBackground()

  val CLASSIC_UI_REPOSITORY_COLORS: List<Color>
    get() = listOf(JBColor.RED, JBColor.GREEN, JBColor.BLUE, JBColor.ORANGE, JBColor.CYAN, JBColor.YELLOW, JBColor.MAGENTA, JBColor.PINK)

  val NEW_UI_REPOSITORY_COLORS: List<Color>
    get() = listOf(
      JBColor(0xFF6285, 0x865762),
      JBColor(0x89C398, 0x436946),
      JBColor(0x6D5FF3, 0x5B53AF),
      JBColor(0xF2B181, 0x825845),
      JBColor(0x32E0D9, 0x389895),
      JBColor(0xFED277, 0x826A41),
      JBColor(0xF95CFF, 0x88458A),
      JBColor(0xBE906D, 0x947259),
      JBColor(0x4FF2B7, 0x178E63),
      JBColor(0xA177F4, 0x583D7A),
      JBColor(0x3FB3B8, 0x227985),
      JBColor(0xB979B9, 0x875887),
      JBColor(0xECCA88, 0x8C7B58),
      JBColor(0xC9CCD6, 0x5A5D6B),
    )

}

@ApiStatus.Internal
object RepositoryColorGeneratorFactory {
  fun <RepoId> create(repoIds: List<RepoId>, vararg additionalColorSpaces: RepositoryColorGenerator.AdditionalColorSpace): RepositoryColorGenerator<RepoId> {
    if (ExperimentalUI.isNewUI()) return RepositoryColorGeneratorImpl(repoIds, RepositoryColors.NEW_UI_REPOSITORY_COLORS, *additionalColorSpaces)
    return forClassicUI(repoIds)
  }

  private fun <RepoId> forClassicUI(repoIds: List<RepoId>): RepositoryColorGenerator<RepoId> {
    val delegate = RepositoryColorGeneratorImpl(repoIds, RepositoryColors.CLASSIC_UI_REPOSITORY_COLORS)
    return ClassicUiRepositoryColorGenerator(delegate)
  }
}

private class RepositoryColorGeneratorImpl<RepoID>(
  repoIds: List<RepoID>,
  mainPalette: List<Color>,
  vararg additionalColorSpaces: RepositoryColorGenerator.AdditionalColorSpace,
): RepositoryColorGenerator<RepoID> {
  private val ids: List<RepoID> = repoIds.toList()
  private val colorSpace2Colors: MutableMap<String, Map<RepoID, Color>> = mutableMapOf()

  init {
    colorSpace2Colors[RepositoryColors.DEFAULT_COLOR_SPACE] = generateFromPalette(mainPalette, ids)

    for (colorSpace in additionalColorSpaces) {
      // do not allow overriding the default palette
      if (colorSpace.colorKey == RepositoryColors.DEFAULT_COLOR_SPACE) continue

      // allow additional palettes only the same size as the default
      if (colorSpace.colors.size != mainPalette.size) continue

      val colors = generateFromPalette(ArrayList<Color>(colorSpace.colors), ids)
      colorSpace2Colors[colorSpace.colorKey] = colors
    }
  }

  override fun getColor(repoId: RepoID, colorSpace: String): Color {
    val repoColors = colorSpace2Colors[colorSpace] ?: colorSpace2Colors[RepositoryColors.DEFAULT_COLOR_SPACE] ?: return RepositoryColors.defaultColor

    val color = repoColors[repoId]
    if (color != null) return color

    LOG.error("No color record for repo id $repoId. All colors: $repoColors")
    return RepositoryColors.defaultColor
  }

  private fun <RepoID> generateFromPalette(colors: List<Color>, ids: List<RepoID>): Map<RepoID, Color> {
    return ids.mapIndexed { index, value ->
      value to generateColorFor(index, ids.size, colors)
    }.toMap()
  }

  private fun generateColorFor(repoNumber: Int, repoCount: Int, colors: List<Color>): Color {
    val color: Color
    val size = colors.size
    if (repoNumber >= size) {
      val balance = ((repoNumber / size).toDouble()) / (repoCount / size)
      val mix = ColorUtil.mix(colors[repoNumber % size], colors[(repoNumber + 1) % size], balance)
      val tones = (abs(balance - 0.5) * 2 * (repoCount / size) + 1).toInt()
      if (mix is JBColor) {
        color = JBColor.lazy(Supplier { JBColor(ColorUtil.darker(mix, tones), ColorUtil.brighter(mix, 2 * tones)) })
      }
      else {
        color = JBColor(ColorUtil.darker(mix, tones), ColorUtil.brighter(mix, 2 * tones))
      }
    }
    else {
      color = colors[repoNumber]
    }
    return color
  }


  companion object {
    private val LOG = Logger.getInstance(RepositoryColorGenerator::class.java)
  }
}

private class ClassicUiRepositoryColorGenerator<RepoId>(val delegate: RepositoryColorGenerator<RepoId>) : RepositoryColorGenerator<RepoId> by delegate {
  override fun getColor(repoId: RepoId, colorSpace: String): Color {
    return delegate.getColor(repoId, colorSpace).mixWithTableBackground()
  }

  private fun Color.mixWithTableBackground(): Color = JBColor.lazy { ColorUtil.mix(this, UIUtil.getTableBackground(), 0.75) }
}
