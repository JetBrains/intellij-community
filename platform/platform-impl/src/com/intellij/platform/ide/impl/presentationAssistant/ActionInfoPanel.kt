// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * @author nik
 */
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Font
import java.awt.font.TextAttribute
import javax.swing.JPanel
import kotlin.math.max

internal class ActionInfoPanel(textData: TextData, private val appearance: ActionInfoPopupGroup.Appearance) : JPanel() {
  private val titleLabel = JBLabel()
  private val subtitleLabel = JBLabel()
  var textData: TextData = textData
    set(value) {
      field = value
      updateLabels()
    }

  init {
    background = appearance.theme.background

    layout = GridLayout()
    border = JBUI.Borders.empty(appearance.popupInsets)
    val titleSubtitleIntersection = if (appearance.titleSubtitleGap < 0) UnscaledGaps(top = -appearance.titleSubtitleGap) else UnscaledGaps.EMPTY

    RowsGridBuilder(this)
      .row(resizable = true).cell(component = titleLabel, verticalAlign = VerticalAlign.CENTER, resizableColumn = true)
      .row(resizable = true).cell(component = subtitleLabel, verticalAlign = VerticalAlign.CENTER, resizableColumn = true, visualPaddings = titleSubtitleIntersection)

    titleLabel.foreground = appearance.theme.foreground

    subtitleLabel.border = JBUI.Borders.empty(max(appearance.titleSubtitleGap, 0), appearance.subtitleHorizontalInset,
                                              0, appearance.subtitleHorizontalInset)
    subtitleLabel.foreground = appearance.theme.keymapLabel
    subtitleLabel.font = DEFAULT_FONT.deriveFont(JBUIScale.scale(appearance.subtitleFontSize))

    updateLabels()
  }

  private fun updateLabels() {
    titleLabel.text = textData.title
    titleLabel.font = (textData.titleFont?.let { JBFont.create(it) } ?: DEFAULT_FONT)
      .deriveFont(JBUIScale.scale (appearance.titleFontSize))
      .deriveFont(mapOf(TextAttribute.WEIGHT to TextAttribute.WEIGHT_BOLD))

    subtitleLabel.isVisible = textData.showSubtitle
    subtitleLabel.text = textData.subtitle ?: " "
  }

  /** Preferred size of popup when both title and subtitle are shown */
  fun getFullSize(): Dimension {
    val oldSubtitleVisible = subtitleLabel.isVisible
    return try {
      subtitleLabel.isVisible = true
      preferredSize
    }
    finally {
      subtitleLabel.isVisible = oldSubtitleVisible
    }
  }

  companion object {
    val DEFAULT_FONT: Font get() = JBFont.label()
  }
}
