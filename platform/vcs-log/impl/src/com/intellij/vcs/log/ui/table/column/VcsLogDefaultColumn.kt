// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column

import com.intellij.openapi.vcs.FilePath
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.text.DateTimeFormatManager
import com.intellij.util.text.JBDateFormat
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.graph.DefaultColorGenerator
import com.intellij.vcs.log.impl.CommonUiProperties
import com.intellij.vcs.log.paint.GraphCellPainter
import com.intellij.vcs.log.paint.SimpleGraphCellPainter
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil
import com.intellij.vcs.log.ui.render.GraphCommitCell
import com.intellij.vcs.log.ui.render.GraphCommitCellRenderer
import com.intellij.vcs.log.ui.table.GraphTableModel
import com.intellij.vcs.log.ui.table.RootCellRenderer
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.ui.table.VcsLogStringCellRenderer
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.*
import javax.swing.table.TableCellRenderer

internal fun getDefaultDynamicColumns() = listOf<VcsLogDefaultColumn<*>>(Author, Hash, Date)

internal sealed class VcsLogDefaultColumn<T>(
  @NonNls override val id: String,
  override val localizedName: @Nls String,
  override val isDynamic: Boolean = true
) : VcsLogColumn<T> {
  /**
   * @return stable name (to identify column in statistics)
   */
  val stableName: String
    get() = id.toLowerCase(Locale.ROOT)
}

internal object Root : VcsLogDefaultColumn<FilePath>("Default.Root", "", false) {
  override val isResizable = false

  override fun getValue(model: GraphTableModel, row: Int): FilePath = model.visiblePack.getFilePath(row)

  override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer = RootCellRenderer(table.properties, table.colorManager)

  override fun getStubValue(model: GraphTableModel): FilePath = VcsUtil.getFilePath(ContainerUtil.getFirstItem(model.logData.roots))
}

internal object Commit : VcsLogDefaultColumn<GraphCommitCell>("Default.Subject", VcsLogBundle.message("vcs.log.column.subject"), false) {
  override fun getValue(model: GraphTableModel, row: Int) =
    GraphCommitCell(
      model.getCommitMetadata(row).subject,
      model.getRefsAtRow(row),
      model.visiblePack.visibleGraph.getRowInfo(row).printElements
    )

  override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer {
    val graphCellPainter: GraphCellPainter = object : SimpleGraphCellPainter(DefaultColorGenerator()) {
      override fun getRowHeight(): Int {
        return table.rowHeight
      }
    }
    return GraphCommitCellRenderer(table.logData, graphCellPainter, table)
  }

  override fun getStubValue(model: GraphTableModel): GraphCommitCell = GraphCommitCell("", emptyList(), emptyList())

}

internal object Author : VcsLogDefaultColumn<String>("Default.Author", VcsLogBundle.message("vcs.log.column.author")) {
  override fun getValue(model: GraphTableModel, row: Int) = CommitPresentationUtil.getAuthorPresentation(model.getCommitMetadata(row))

  override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer = VcsLogStringCellRenderer(true)

  override fun getStubValue(model: GraphTableModel) = ""
}

internal object Date : VcsLogDefaultColumn<String>("Default.Date", VcsLogBundle.message("vcs.log.column.date")) {
  override fun getValue(model: GraphTableModel, row: Int): String {
    val properties = model.properties
    val commit = model.getCommitMetadata(row)
    val preferCommitDate = properties.exists(CommonUiProperties.PREFER_COMMIT_DATE) && properties.get(CommonUiProperties.PREFER_COMMIT_DATE)
    val timeStamp = if (preferCommitDate) commit.commitTime else commit.authorTime
    return if (timeStamp < 0) "" else JBDateFormat.getFormatter().formatPrettyDateTime(timeStamp)
  }

  override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer = VcsLogStringCellRenderer(
    contentSampleProvider = {
      if (DateTimeFormatManager.getInstance().isPrettyFormattingAllowed) {
        null
      }
      else {
        JBDateFormat.getFormatter().formatDateTime(DateFormatUtil.getSampleDateTime())
      }
    }
  )

  override fun getStubValue(model: GraphTableModel): String = ""
}

internal object Hash : VcsLogDefaultColumn<String>("Default.Hash", VcsLogBundle.message("vcs.log.column.hash")) {
  override fun getValue(model: GraphTableModel, row: Int): String = model.getCommitMetadata(row).id.toShortString()

  override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer = VcsLogStringCellRenderer(
    contentSampleProvider = { "e".repeat(VcsLogUtil.SHORT_HASH_LENGTH) }
  )

  override fun getStubValue(model: GraphTableModel): String = ""
}