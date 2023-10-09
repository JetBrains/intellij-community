// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * @author nik
 */
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import java.awt.font.TextAttribute
import javax.swing.JPanel

internal class ActionInfoPanel(textData: TextData, private val appearance: ActionInfoPopupGroup.Appearance) : JPanel() {
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
    val titleSubtitleIntersection = if (appearance.titleSubtitleGap < 0) UnscaledGaps(bottom = -appearance.titleSubtitleGap) else UnscaledGaps.EMPTY

    RowsGridBuilder(this)
      .row(resizable = true).cell(component = titleLabel, verticalAlign = VerticalAlign.CENTER, resizableColumn = true, visualPaddings = titleSubtitleIntersection)
      .row(resizable = true).cell(component = subtitleLabel, verticalAlign = VerticalAlign.CENTER, resizableColumn = true)

    titleLabel.border = JBEmptyBorder(appearance.titleInsets.unscaled)
    titleLabel.foreground = TITLE_COLOR

    subtitleLabel.border = JBEmptyBorder(appearance.subtitleInsets.unscaled.apply {
      if (appearance.titleSubtitleGap > 0) top += appearance.titleSubtitleGap
    })
    subtitleLabel.foreground = SUBTITLE_COLOR
    subtitleLabel.font = JBFont.label().deriveFont(JBUIScale.scale(appearance.subtitleFontSize))

    updateLabels()
  }

  private fun updateLabels() {
    titleLabel.text = textData.title
    titleLabel.font = (textData.titleFont?.let { JBFont.create(it) } ?: JBFont.label())
      .deriveFont(JBUIScale.scale (appearance.titleFontSize))
      .deriveFont(mapOf(TextAttribute.WEIGHT to TextAttribute.WEIGHT_BOLD))

    val subtitle = textData.subtitle
    subtitleLabel.text = subtitle ?: " "
  }

  companion object {
    val BACKGROUND = JBColor.namedColor("PresentationAssistant.Popup.background", JBColor.PanelBackground)
    private val TITLE_COLOR = JBColor.namedColor("PresentationAssistant.Popup.foreground", JBColor.foreground())
    private val SUBTITLE_COLOR = JBColor.namedColor("PresentationAssistant.keymapLabel", JBColor.foreground())
  }
}
