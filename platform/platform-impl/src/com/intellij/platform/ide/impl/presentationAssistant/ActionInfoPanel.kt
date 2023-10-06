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
    private const val TITLE_FONT_SIZE = 40f
    private const val SUBTITLE_FONT_SIZE = 14f
    private const val CORNER_RADIUS = 8
    val BACKGROUND = EditorColorsManager.getInstance().globalScheme.getColor(BACKGROUND_COLOR_KEY)
    private val TITLE_COLOR = EditorColorsManager.getInstance().globalScheme.getColor(FOREGROUND_COLOR_KEY)
  }
}

//private fun JComponent.addComponentsWithGap(components: List<JComponent>, gap: Int) {
//  val builder = RowsGridBuilder(this)
//  val row = builder.row(resizable = true)
//  for (c in components) {
//    row.cell(c)
//  }
//
//  row.columnsGaps((0 until row.columnsCount).map {
//    val rightGap = if (it == (row.columnsCount - 1)) 0 else gap
//    UnscaledGapsX(0, rightGap)
//  })
//}


//private fun List<Pair<String, Font?>>.mergeFragments(): List<Pair<String, Font?>> {
//  val result = ArrayList<Pair<String, Font?>>()
//  for (item in this) {
//    val last = result.lastOrNull()
//    if (last != null && last.second == item.second) {
//      result.removeAt(result.lastIndex)
//      result.add(Pair(last.first + item.first, last.second))
//    }
//    else {
//      result.add(item)
//    }
//  }
//  return result
//}
//
//private fun createLabels(textFragments: List<Pair<String, Font?>>, ideFrame: IdeFrame): List<JLabel> {
//  var fontSize = configuration.fontSize.toFloat()
//  val color = EditorColorsManager.getInstance().globalScheme.getColor(FOREGROUND_COLOR_KEY)
//  val labels = textFragments.mergeFragments().map {
//    @Suppress("HardCodedStringLiteral")
//    val label = JLabel("<html>${it.first}</html>", SwingConstants.CENTER)
//    label.foreground = color
//    if (it.second != null) label.font = it.second
//    label
//  }
//
//  fun setFontSize(size: Float) {
//    for (label in labels) {
//      label.font = label.font.deriveFont(size)
//    }
//    val maxAscent = labels.maxOfOrNull { it.getFontMetrics(it.font).maxAscent } ?: 0
//    for (label in labels) {
//      val ascent = label.getFontMetrics(label.font).maxAscent
//      if (ascent < maxAscent) {
//        label.border = BorderFactory.createEmptyBorder(maxAscent - ascent, 0, 0, 0)
//      }
//      else {
//        label.border = null
//      }
//    }
//  }
//  setFontSize(fontSize)
//  val frameWidth = ideFrame.component.width
//  if (frameWidth > 100) {
//    while (labels.sumOf { it.preferredSize.width } > frameWidth - 10 && fontSize > 12) {
//      setFontSize(--fontSize)
//    }
//  }
//  return labels
//}
