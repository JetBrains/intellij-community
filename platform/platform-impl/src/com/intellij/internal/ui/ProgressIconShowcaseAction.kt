// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.AnimatedIcon.Frame
import com.intellij.ui.ColorPicker
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.picker.ColorListener
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.SVGLoader
import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.UIManager

/**
 * @author Konstantin Bulenkov
 */
class ProgressIconShowcaseAction: DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val panel = panel {
      row {
        icon(ProgressIcon())
        link("Change color") {
          ColorPicker.showColorPickerPopup(null, getIconColor(), object : ColorListener {
            override fun colorChanged(color: Color?, source: Any?) {
              if (color != null) {
                UIManager.put("ProgressIcon.color", color)
              }
            }
          })
        }
      }
    }
    val dialog = dialog(templatePresentation.text, panel)
    dialog.isModal = false
    dialog.setSize(600, 600)
    dialog.show()
  }
}

class ProgressIcon: AnimatedIcon(*createFrames())

val opacities = arrayOf(1.0, 0.915, 0.83, 0.745, 0.66, 0.575, 0.49, 0.405, 0.32, 0.235, 0.15, 0.065)
val paths = arrayOf("M8 2V4",
                    "M5 2.80383L6 4.53588",
                    "M2.80396 5L4.53601 6",
                    "M2.00024 8L4.00024 8",
                    "M2.80396 11L4.53601 10",
                    "M5.00024 13.1962L6.00024 11.4641",
                    "M8.00024 14L8.00024 12",
                    "M11.0002 13.1962L10.0002 11.4641",
                    "M13.1965 11L11.4645 10",
                    "M14.0002 8L12.0002 8",
                    "M13.1965 5L11.4645 6",
                    "M11.0002 2.80383L10.0002 4.53588")
val iconCache = arrayOfNulls<Icon>(paths.size)
var iconCacheKey = ""

fun currentCacheKey() = ColorUtil.toHex(getIconColor())

fun getIconColor() = JBColor.namedColor("ProgressIcon.color", JBColor(0x767A8A, 0xCED0D6))

fun createFrames() = Array(paths.size) { createFrame(it) }

fun createFrame(i: Int): Frame {
  return object : Frame {
    override fun getIcon() = getIconFromCache(i)
    override fun getDelay() = Integer.getInteger("ProgressIcon.delay", 100)
  }
}

private fun getIconFromCache(i: Int): Icon {
  val icon = iconCache[i]
  if (icon != null && iconCacheKey == currentCacheKey()) {
    return icon
  }
  iconCache.fill(null)
  val stroke = currentCacheKey()
  var s = """
  <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
  """
  for (n in paths.indices) {
    val opacity = opacities[(n + i) % opacities.size]
    s += """
      <path opacity="$opacity" d="${paths[n]}" stroke="#$stroke" stroke-linecap="round"/>
    """.trimIndent()
  }
  s += "</svg>"
  val scaleContext = ScaleContext.create()
  val image = SVGLoader.load(s.byteInputStream(), scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat())

  iconCache[i] = IconUtil.toRetinaAwareIcon(image as BufferedImage)
  iconCacheKey = currentCacheKey()
  return iconCache[i]!!
}


