// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.render.CompositeRenderer
import com.intellij.util.ui.FontInfo
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Font
import java.awt.RenderingHints
import java.util.function.Supplier
import javax.swing.ListCellRenderer

class FontInfoRendererBuilder {

  private var isEditorFont: Boolean = false
  private var allFonts: Supplier<List<FontInfo>>? = null
  private var monoFonts: Supplier<List<FontInfo>>? = null

  /**
   * The fonts are used in the editor
   */
  fun editorFont(): FontInfoRendererBuilder {
    this.isEditorFont = true
    return this
  }

  /**
   * TODO: This kind of separator selection has poor performance.
   * It has been implemented in such a way and can be improved if needed.
   * Don't move this method to the public API.
   */
  @ApiStatus.Experimental
  @ApiStatus.Internal
  fun withSeparatorFontType(allFonts: Supplier<List<FontInfo>>?, monoFonts: Supplier<List<FontInfo>>?): FontInfoRendererBuilder {
    this.allFonts = allFonts
    this.monoFonts = monoFonts
    return this
  }

  fun build(): ListCellRenderer<FontInfo?> {
    return createFontInfoRenderer(isEditorFont, allFonts, monoFonts)
  }

  /**
   * Relates to [com.intellij.ui.FontComboBox] only and supports specific elements of the combobox.
   */
  @ApiStatus.Internal
  fun buildFontComboBoxRenderer(): ListCellRenderer<Any?> {
    val fontRenderer = build()

    val otherRenderer = listCellRenderer<Any?>("") {
      val value = value
      when (value) {
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

    return object : CompositeRenderer<Any?>() {
      override fun selectRenderer(value: Any?): ListCellRenderer<*> {
        return when (value) {
          is FontInfo -> fontRenderer
          else -> otherRenderer
        }
      }
    }
  }
}

private fun createFontInfoRenderer(
  isEditorFont: Boolean,
  allFonts: Supplier<List<FontInfo>>?,
  monoFonts: Supplier<List<FontInfo>>?,
): ListCellRenderer<FontInfo?> {
  return listCellRenderer("") {
    val value = value
    val index = index

    // Calculating preferred width can be quite consuming though (in particular, when a large number of fonts is available),
    // so we avoid such a calculation here.
    rowWidth = JBUI.scale(50)

    val allFonts = allFonts?.get()
    val monoFonts = monoFonts?.get()

    when (value) {
      monoFonts?.getOrNull(0) -> {
        separator { text = ApplicationBundle.message("settings.editor.font.monospaced") }
      }
      allFonts?.find { !it.isMonospaced } -> {
        separator { text = ApplicationBundle.message("settings.editor.font.proportional") }
      }
    }

    val displayFont = getDisplayFont(value, index)
    val customRenderingHints = mapOf(RenderingHints.KEY_TEXT_ANTIALIASING to AntialiasingType.getKeyForCurrentScope(isEditorFont))

    text(value.toString()) {
      if (displayFont != null) {
        font = displayFont
      }
      renderingHints = customRenderingHints
    }

    if (displayFont == null) {
      text(IdeBundle.message("font.info.renderer.non.latin")) {
        foreground = greyForeground
        renderingHints = customRenderingHints
      }
    }
  }
}

private fun getDisplayFont(fontInfo: FontInfo, index: Int): Font? {
  val labelFont = JBUI.Fonts.label()
  val valueFont = fontInfo.getFont(labelFont.getSize())

  return when {
    index == -1 -> labelFont
    valueFont.canDisplayUpTo(fontInfo.toString()) == -1 -> valueFont
    else -> null
  }
}
