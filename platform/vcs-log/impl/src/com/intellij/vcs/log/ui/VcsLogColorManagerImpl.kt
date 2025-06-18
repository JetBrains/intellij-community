// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.util.function.Supplier
import kotlin.math.abs

/**
 * @author Kirill Likhodedov
 */
@ApiStatus.Internal
class VcsLogColorManagerImpl internal constructor(
  paths: Collection<FilePath>,
  mainPalette: List<Color>,
  vararg additionalColorSpaces: AdditionalColorSpace
) : VcsLogColorManager {
  private val myPath2Palette: Map<String, Map<String, Color>>
  private val myPaths: List<FilePath>

  init {
    var defaultPalette = mainPalette
    myPaths = paths.toList()
    myPath2Palette = mutableMapOf()

    defaultPalette = if (defaultPalette.isEmpty()) mutableListOf<Color>(defaultRootColor) else ArrayList<Color>(defaultPalette)

    myPath2Palette[VcsLogColorManager.DEFAULT_COLOR_MODE] = generateFromPalette(defaultPalette)

    for (colorSpace in additionalColorSpaces) {
      // do not allow to override default palette
      if (colorSpace.colorMode == VcsLogColorManager.DEFAULT_COLOR_MODE) continue

      // allow additional palettes only the same size as the default
      if (colorSpace.palette.size != defaultPalette.size) continue

      val colors = generateFromPalette(ArrayList<Color>(colorSpace.palette))
      myPath2Palette[colorSpace.colorMode] = colors
    }
  }

  private fun generateFromPalette(defaultPalette: List<Color>): Map<String, Color> {
    val path2Palette: MutableMap<String, Color> = mutableMapOf()

    for (i in myPaths.indices) {
      path2Palette[myPaths[i].getPath()] = getColor(i, myPaths.size, defaultPalette)
    }
    return path2Palette
  }

  override fun getPathColor(path: FilePath, colorMode: String): Color {
    return getColor(path.getPath(), colorMode)
  }

  override fun getRootColor(root: VirtualFile, colorMode: String): Color {
    return getColor(root.getPath(), colorMode)
  }

  private fun getColor(path: String, colorMode: String): Color {
    val paletteToColor = myPath2Palette.getOrDefault(colorMode, myPath2Palette[VcsLogColorManager.DEFAULT_COLOR_MODE])!!
    var color = paletteToColor[path]
    if (color == null) {
      LOG.error("No color record for path $path. All paths: $paletteToColor")
      color = defaultRootColor
    }
    return color
  }

  override fun getPaths(): Collection<FilePath> = myPaths

  class AdditionalColorSpace(val colorMode: String, val palette: List<Color>)

  companion object {
    private val LOG = Logger.getInstance(VcsLogColorManagerImpl::class.java)

    private fun getColor(rootNumber: Int, rootsCount: Int, palette: List<Color>): Color {
      val color: Color
      val size = palette.size
      if (rootNumber >= size) {
        val balance = ((rootNumber / size).toDouble()) / (rootsCount / size)
        val mix = ColorUtil.mix(palette[rootNumber % size], palette[(rootNumber + 1) % size], balance)
        val tones = (abs(balance - 0.5) * 2 * (rootsCount / size) + 1).toInt()
        if (mix is JBColor) {
          color = JBColor.lazy(Supplier { JBColor(ColorUtil.darker(mix, tones), ColorUtil.brighter(mix, 2 * tones)) })
        }
        else {
          color = JBColor(ColorUtil.darker(mix, tones), ColorUtil.brighter(mix, 2 * tones))
        }
      }
      else {
        color = palette[rootNumber]
      }
      return color
    }

    private val defaultRootColor: Color
      get() = UIUtil.getTableBackground()
  }
}
