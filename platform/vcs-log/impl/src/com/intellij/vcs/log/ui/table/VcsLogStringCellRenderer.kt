// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table

import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.vcs.log.ui.render.GraphCommitCellRenderer.BorderlessTableCellState
import com.intellij.vcs.log.util.VcsLogUiUtil
import org.jetbrains.annotations.Nls
import java.awt.Font
import javax.swing.JTable

/**
 * @param contentSampleProvider used to estimate the width of the column,
 * null if content width may vary significantly and width cannot be estimated from the sample.
 */
class VcsLogStringCellRenderer internal constructor(
  private val withSpeedSearchHighlighting: Boolean = false,
  private val contentSampleProvider: (() -> @Nls String?)? = null
) : ColoredTableCellRenderer(), VcsLogCellRenderer {

  constructor(contentSampleProvider: (() -> @Nls String?)? = null) : this(withSpeedSearchHighlighting = false,
                                                                          contentSampleProvider = contentSampleProvider)

  init {
    cellState = BorderlessTableCellState()
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

  override fun getPreferredWidth(table: JTable): Int? {
    val sample = contentSampleProvider?.let { provider -> provider() } ?: return null
    return table.getFontMetrics(VcsLogGraphTable.getTableFont().deriveFont(Font.BOLD)).stringWidth(sample) +
           VcsLogUiUtil.getHorizontalTextPadding(this)
  }
}