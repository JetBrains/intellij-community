// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FilePath
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.text.DateTimeFormatManager
import com.intellij.util.text.JBDateFormat
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.graph.DefaultColorGenerator
import com.intellij.vcs.log.impl.CommonUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties
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
import org.jetbrains.annotations.ApiStatus
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

  override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer {
    doOnPropertyChange(table) { property ->
      if (CommonUiProperties.SHOW_ROOT_NAMES == property) {
        table.rootColumnUpdated()
      }
    }
    return RootCellRenderer(table.properties, table.colorManager)
  }

  override fun getStubValue(model: GraphTableModel): FilePath = VcsUtil.getFilePath(ContainerUtil.getFirstItem(model.logData.roots))
}

internal object Commit : VcsLogDefaultColumn<GraphCommitCell>("Default.Subject", VcsLogBundle.message("vcs.log.column.subject"), false),
                         VcsLogMetadataColumn {
  override fun getValue(model: GraphTableModel, row: Int) =
    GraphCommitCell(
      getValue(model, model.getCommitMetadata(row)),
      model.getRefsAtRow(row),
      model.visiblePack.visibleGraph.getRowInfo(row).printElements
    )

  override fun getValue(model: GraphTableModel, commit: VcsCommitMetadata): String = commit.subject

  override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer {
    val graphCellPainter: GraphCellPainter = object : SimpleGraphCellPainter(DefaultColorGenerator()) {
      override fun getRowHeight(): Int {
        return table.rowHeight
      }
    }

    val commitCellRenderer = GraphCommitCellRenderer(table.logData, graphCellPainter, table)
    commitCellRenderer.setCompactReferencesView(table.properties[CommonUiProperties.COMPACT_REFERENCES_VIEW])
    commitCellRenderer.setShowTagsNames(table.properties[CommonUiProperties.SHOW_TAG_NAMES])
    commitCellRenderer.setLeftAligned(table.properties[CommonUiProperties.LABELS_LEFT_ALIGNED])

    doOnPropertyChange(table) { property ->
      if (CommonUiProperties.COMPACT_REFERENCES_VIEW == property) {
        commitCellRenderer.setCompactReferencesView(table.properties[CommonUiProperties.COMPACT_REFERENCES_VIEW])
        table.repaint()
      }
      else if (CommonUiProperties.SHOW_TAG_NAMES == property) {
        commitCellRenderer.setShowTagsNames(table.properties[CommonUiProperties.SHOW_TAG_NAMES])
        table.repaint()
      }
      else if (CommonUiProperties.LABELS_LEFT_ALIGNED == property) {
        commitCellRenderer.setLeftAligned(table.properties[CommonUiProperties.LABELS_LEFT_ALIGNED])
        table.repaint()
      }
    }
    updateTableOnCommitDetailsLoaded(this, table)

    return commitCellRenderer
  }

  override fun getStubValue(model: GraphTableModel): GraphCommitCell = GraphCommitCell("", emptyList(), emptyList())

}

internal object Author : VcsLogDefaultColumn<String>("Default.Author", VcsLogBundle.message("vcs.log.column.author")),
                         VcsLogMetadataColumn {
  override fun getValue(model: GraphTableModel, row: Int) = getValue(model, model.getCommitMetadata(row))
  override fun getValue(model: GraphTableModel, commit: VcsCommitMetadata) = CommitPresentationUtil.getAuthorPresentation(commit)

  override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer {
    updateTableOnCommitDetailsLoaded(this, table)
    return VcsLogStringCellRenderer(true)
  }

  override fun getStubValue(model: GraphTableModel) = ""
}

internal object Date : VcsLogDefaultColumn<String>("Default.Date", VcsLogBundle.message("vcs.log.column.date")), VcsLogMetadataColumn {
  override fun getValue(model: GraphTableModel, row: Int): String {
    return getValue(model, model.getCommitMetadata(row))
  }

  override fun getValue(model: GraphTableModel, commit: VcsCommitMetadata): String {
    val properties = model.properties
    val preferCommitDate = properties.exists(CommonUiProperties.PREFER_COMMIT_DATE) && properties.get(CommonUiProperties.PREFER_COMMIT_DATE)
    val timeStamp = if (preferCommitDate) commit.commitTime else commit.authorTime
    return if (timeStamp < 0) "" else JBDateFormat.getFormatter().formatPrettyDateTime(timeStamp)
  }

  override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer {
    doOnPropertyChange(table) { property ->
      if (property == CommonUiProperties.PREFER_COMMIT_DATE && table.getTableColumn(this@Date) != null) {
        table.repaint()
      }
    }
    updateTableOnCommitDetailsLoaded(this, table)
    return VcsLogStringCellRenderer(
      withSpeedSearchHighlighting = true,
      contentSampleProvider = {
        if (DateTimeFormatManager.getInstance().isPrettyFormattingAllowed) {
          null
        }
        else {
          JBDateFormat.getFormatter().formatDateTime(DateFormatUtil.getSampleDateTime())
        }
      }
    )
  }

  override fun getStubValue(model: GraphTableModel): String = ""
}

internal object Hash : VcsLogDefaultColumn<String>("Default.Hash", VcsLogBundle.message("vcs.log.column.hash")), VcsLogMetadataColumn {
  override fun getValue(model: GraphTableModel, row: Int): String = getValue(model, model.getCommitMetadata(row))
  override fun getValue(model: GraphTableModel, commit: VcsCommitMetadata) = commit.id.toShortString()

  override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer {
    updateTableOnCommitDetailsLoaded(this, table)
    return VcsLogStringCellRenderer(
      withSpeedSearchHighlighting = true,
      contentSampleProvider = { "e".repeat(VcsLogUtil.SHORT_HASH_LENGTH) }
    )
  }

  override fun getStubValue(model: GraphTableModel): String = ""
}

private fun updateTableOnCommitDetailsLoaded(column: VcsLogColumn<*>, graphTable: VcsLogGraphTable) {
  val miniDetailsLoadedListener = Runnable { graphTable.onColumnDataChanged(column) }
  graphTable.logData.miniDetailsGetter.addDetailsLoadedListener(miniDetailsLoadedListener)
  Disposer.register(graphTable) {
    graphTable.logData.miniDetailsGetter.removeDetailsLoadedListener(miniDetailsLoadedListener)
  }
}

private fun doOnPropertyChange(graphTable: VcsLogGraphTable, listener: (VcsLogUiProperties.VcsLogUiProperty<*>) -> Unit) {
  val propertiesChangeListener = object : VcsLogUiProperties.PropertiesChangeListener {
    override fun <T : Any?> onPropertyChanged(property: VcsLogUiProperties.VcsLogUiProperty<T>) {
      listener(property)
    }
  }
  graphTable.properties.addChangeListener(propertiesChangeListener)
  Disposer.register(graphTable) {
    graphTable.properties.removeChangeListener(propertiesChangeListener)
  }
}

@ApiStatus.Internal
interface VcsLogMetadataColumn {
  @ApiStatus.Internal
  fun getValue(model: GraphTableModel, commit: VcsCommitMetadata): @NlsSafe String
}