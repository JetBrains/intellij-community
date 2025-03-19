// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.vcs.log.VcsLogHighlighter
import com.intellij.vcs.log.ui.render.GraphCommitCellRenderer.VcsLogTableCellState
import com.intellij.vcs.log.util.VcsLogUiUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Font
import javax.swing.JTable

/**
 * @param contentSampleProvider used to estimate the width of the column,
 * null if content width may vary significantly and width cannot be estimated from the sample.
 */
@ApiStatus.Internal
class VcsLogStringCellRenderer internal constructor(
  private val withSpeedSearchHighlighting: Boolean = false,
  private val contentSampleProvider: (() -> @Nls String?)? = null
) : ColoredTableCellRenderer(), VcsLogCellRenderer {

  constructor(contentSampleProvider: (() -> @Nls String?)? = null) : this(withSpeedSearchHighlighting = false,
                                                                          contentSampleProvider = contentSampleProvider)

  init {
    cellState = VcsLogTableCellState()
  }

  override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
    if (value == null || table !is VcsLogGraphTable) {
      return
    }
    @Suppress("HardCodedStringLiteral")
    append(value.toString(), table.applyHighlighters(this, row, column, hasFocus, selected))
    if (withSpeedSearchHighlighting) {
      SpeedSearchUtil.applySpeedSearchHighlighting(table, this, false, selected)
    }
  }

  override fun getPreferredWidth(): VcsLogCellRenderer.PreferredWidth {
    val sample = contentSampleProvider?.invoke()
    if (sample == null) {
      return VcsLogCellRenderer.PreferredWidth.FromData { table, value, row, column ->
        if (value.toString().isEmpty()) return@FromData null

        val fontStyle = when ((table as VcsLogGraphTable).getStyle(row, column, false, false, false).textStyle) {
          VcsLogHighlighter.TextStyle.BOLD -> Font.BOLD
          VcsLogHighlighter.TextStyle.ITALIC -> Font.ITALIC
          else -> null
        }
        getStringWidth(table, "$value*", fontStyle)
      }
    }
    return VcsLogCellRenderer.PreferredWidth.Fixed { table -> getStringWidth(table, sample, Font.BOLD) }
  }

  private fun getStringWidth(table: JTable, sample: @Nls String, fontStyle: Int?): Int {
    val tableFont = VcsLogGraphTable.getTableFont()
    val derivedFont = fontStyle?.let { tableFont.deriveFont(it) } ?: tableFont
    return table.getFontMetrics(derivedFont).stringWidth(sample) +
           VcsLogUiUtil.getHorizontalTextPadding(this)
  }
}