// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.FontInfo
import com.intellij.util.ui.JBUI
import java.awt.Font
import java.awt.RenderingHints
import java.util.function.Supplier
import javax.swing.ListCellRenderer

internal fun createFontComboBoxRenderer(
  allFonts: Supplier<List<FontInfo>>,
  monoFonts: Supplier<List<FontInfo>>,
): ListCellRenderer<Any?> {
  return listCellRenderer("") {
    val value = value
    val index = index

    when (value) {
      is FontInfo -> {
        val allFonts = allFonts.get()
        val monoFonts = monoFonts.get()

        when (value) {
          monoFonts.getOrNull(0) -> {
            separator { text = ApplicationBundle.message("settings.editor.font.monospaced") }
          }
          allFonts.find { !it.isMonospaced } -> {
            separator { text = ApplicationBundle.message("settings.editor.font.proportional") }
          }
        }

        val valueText = value.toString()
        val displayFont = getFontInfoDisplayFont(value, index)

        text(valueText) {
          if (displayFont != null) {
            font = displayFont
            renderingHints = mapOf(RenderingHints.KEY_TEXT_ANTIALIASING to AntialiasingType.getKeyForCurrentScope(false))
          }
        }

        if (displayFont == null) {
          text(IdeBundle.message("font.info.renderer.non.latin")) {
            foreground = greyForeground
          }
        }
      }

      is FontComboBox.Model.NoFontItem -> {
        text(value.toString())
      }

      is FontComboBox.Model.LoadingFontsItem -> {
        text(value.toString()) {
          foreground = greyForeground
        }
      }

      is String -> {
        text(value)
      }
    }
  }
}

private fun getFontInfoDisplayFont(fontInfo: FontInfo, index: Int): Font? {
  val labelFont = JBUI.Fonts.label()
  val valueFont = fontInfo.getFont(labelFont.getSize())

  return when {
    index == -1 -> labelFont
    valueFont.canDisplayUpTo(fontInfo.toString()) == -1 -> valueFont
    else -> null
  }
}
