// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.render

import com.intellij.vcs.log.graph.EdgePrintElement
import com.intellij.vcs.log.graph.PrintElement
import com.intellij.vcs.log.paint.PaintParameters
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import org.jetbrains.annotations.ApiStatus
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
object GraphCommitCellUtil {
  private const val MAX_GRAPH_WIDTH: Int = 6

  @JvmStatic
  fun getGraphWidth(table: VcsLogGraphTable, printElements: Collection<PrintElement>): Int {
    if (printElements.isEmpty()) return 0

    var maxIndex = 0.0
    for (printElement in printElements) {
      maxIndex = max(maxIndex, printElement.positionInCurrentRow.toDouble())
      if (printElement is EdgePrintElement) {
        maxIndex = max(maxIndex,
                       (printElement.positionInCurrentRow + printElement.positionInOtherRow) / 2.0)
      }
    }
    maxIndex++
    maxIndex = max(maxIndex, min(MAX_GRAPH_WIDTH.toDouble(), table.visibleGraph.recommendedWidth.toDouble()))

    val rowHeight = table.rowHeight
    return (maxIndex * PaintParameters.getElementWidth(rowHeight)).toInt() + PaintParameters.getGraphTextGap(rowHeight).toInt()
  }
}