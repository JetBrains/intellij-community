// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * @author nik
 */
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import javax.swing.JPanel

internal class ActionInfoPanel(textData: TextData) : JPanel() {
  private val titleLabel = JBLabel()
  private val subtitleLabel = JBLabel()
  var textData: TextData = textData
    set(value) {
      field = value
      updateLabels()
    }

  init {
    background = BACKGROUND

    layout = GridLayout()
    RowsGridBuilder(this)
      .row(resizable = true).cell(component = titleLabel, verticalAlign = VerticalAlign.CENTER, resizableColumn = true)
      .row(resizable = true).cell(component = subtitleLabel, verticalAlign = VerticalAlign.CENTER, resizableColumn = true)

    titleLabel.border = JBEmptyBorder(6, 16, 0, 16)
    titleLabel.foreground = TITLE_COLOR

    subtitleLabel.border = JBEmptyBorder(0, 18, 8, 16)
    subtitleLabel.foreground = TITLE_COLOR
    subtitleLabel.font = JBFont.label().deriveFont(JBUIScale.scale(SUBTITLE_FONT_SIZE))

    updateLabels()
  }

  private fun updateLabels() {
    titleLabel.text = "<html>${textData.title}</html>"
    titleLabel.font = (font?.let { JBFont.create(it) } ?: JBFont.label()).deriveFont(JBUIScale.scale(TITLE_FONT_SIZE))

    val subtitle = textData.subtitle
    subtitleLabel.text = subtitle?.let { "<html>${subtitle}</html>" } ?: " "
  }

  companion object {
    val BACKGROUND = EditorColorsManager.getInstance().globalScheme.getColor(BACKGROUND_COLOR_KEY)
    private const val TITLE_FONT_SIZE = 40f
    private const val SUBTITLE_FONT_SIZE = 14f
    private val TITLE_COLOR = EditorColorsManager.getInstance().globalScheme.getColor(FOREGROUND_COLOR_KEY)
  }
}
