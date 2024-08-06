// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table.column

import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FilePath
import com.intellij.ui.ExperimentalUI
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.text.DateTimeFormatManager
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.data.LoadingDetails
import com.intellij.vcs.log.graph.DefaultColorGenerator
import com.intellij.vcs.log.history.FileHistoryPaths.filePathOrDefault
import com.intellij.vcs.log.history.FileHistoryPaths.hasPathsInformation
import com.intellij.vcs.log.impl.CommonUiProperties
import com.intellij.vcs.log.impl.onPropertyChange
import com.intellij.vcs.log.paint.GraphCellPainter
import com.intellij.vcs.log.paint.SimpleGraphCellPainter
import com.intellij.vcs.log.ui.VcsLogBookmarkReferenceProvider.Companion.getBookmarkRefs
import com.intellij.vcs.log.ui.VcsLogBookmarksListener
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil
import com.intellij.vcs.log.ui.render.GraphCommitCell
import com.intellij.vcs.log.ui.render.GraphCommitCellRenderer
import com.intellij.vcs.log.ui.table.*
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.VisiblePack
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

internal data object Root : VcsLogDefaultColumn<FilePath>("Default.Root", "", false) {
  override val isResizable = false

  override fun getValue(model: GraphTableModel, row: Int): FilePath? {
    val visiblePack = model.visiblePack
    if (visiblePack.hasPathsInformation()) {
      val path = visiblePack.filePathOrDefault(model.getRowInfo(row).commit)
      if (path != null) {
        return path
      }
    }
    return model.getRootAtRow(row)?.let(VcsUtil::getFilePath)
  }

  override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer {
    table.properties.onPropertyChange(table) { property ->
      if (CommonUiProperties.SHOW_ROOT_NAMES == property) {
        table.rootColumnUpdated()
      }
    }

    if (ExperimentalUI.isNewUI()) return NewUiRootCellRenderer(table.properties, table.colorManager)
    return RootCellRenderer(table.properties, table.colorManager)
  }

  override fun getStubValue(model: GraphTableModel): FilePath = VcsUtil.getFilePath(model.logData.roots.first())
}

internal object Commit : VcsLogDefaultColumn<GraphCommitCell>("Default.Subject", VcsLogBundle.message("vcs.log.column.subject"), false),
                         VcsLogMetadataColumn {
  override fun getValue(model: GraphTableModel, row: Int): GraphCommitCell {
    val printElements = if (VisiblePack.NO_GRAPH_INFORMATION.get(model.visiblePack, false)) emptyList()
    else model.getRowInfo(row).printElements

    val metadata = model.getCommitMetadata(row, true)
    return GraphCommitCell(
      getValue(model, metadata),
      model.getRefsAtRow(row),
      if (metadata !is LoadingDetails) getBookmarkRefs(model.logData.project, metadata.id, metadata.root) else emptyList(),
      printElements
    )
  }

  override fun getValue(model: GraphTableModel, commit: VcsCommitMetadata): String = commit.subject

  override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer {
    val graphCellPainter: GraphCellPainter = object : SimpleGraphCellPainter(service<DefaultColorGenerator>()) {
      override val rowHeight: Int get() = table.rowHeight
    }

    val commitCellRenderer = GraphCommitCellRenderer(table.logData, graphCellPainter, table)
    commitCellRenderer.setCompactReferencesView(table.properties[CommonUiProperties.COMPACT_REFERENCES_VIEW])
    commitCellRenderer.setShowTagsNames(table.properties[CommonUiProperties.SHOW_TAG_NAMES])
    commitCellRenderer.setLeftAligned(table.properties[CommonUiProperties.LABELS_LEFT_ALIGNED])

    table.properties.onPropertyChange(table) { property ->
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

    table.logData.project.messageBus.connect(table).subscribe(VcsLogBookmarksListener.TOPIC, object : VcsLogBookmarksListener {
      override fun logBookmarksChanged() {
        table.repaint()
      }
    })

    return commitCellRenderer
  }

  override fun getStubValue(model: GraphTableModel): GraphCommitCell = GraphCommitCell("", emptyList(), emptyList(), emptyList())

}

internal object Author : VcsLogDefaultColumn<String>("Default.Author", VcsLogBundle.message("vcs.log.column.author")),
                         VcsLogMetadataColumn {
  override fun getValue(model: GraphTableModel, row: Int) = getValue(model, model.getCommitMetadata(row, true))
  override fun getValue(model: GraphTableModel, commit: VcsCommitMetadata) = CommitPresentationUtil.getAuthorPresentation(commit)

  override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer {
    updateTableOnCommitDetailsLoaded(this, table)
    return VcsLogStringCellRenderer(true)
  }

  override fun getStubValue(model: GraphTableModel) = ""
}

internal object Date : VcsLogDefaultColumn<String>("Default.Date", VcsLogBundle.message("vcs.log.column.date")), VcsLogMetadataColumn {
  override fun getValue(model: GraphTableModel, row: Int): String {
    return getValue(model, model.getCommitMetadata(row, true))
  }

  override fun getValue(model: GraphTableModel, commit: VcsCommitMetadata): String {
    val properties = model.properties
    val preferCommitDate = properties.exists(CommonUiProperties.PREFER_COMMIT_DATE) && properties[CommonUiProperties.PREFER_COMMIT_DATE]
    val timeStamp = if (preferCommitDate) commit.commitTime else commit.authorTime
    return if (timeStamp < 0) "" else DateFormatUtil.formatPrettyDateTime(timeStamp)
  }

  override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer {
    table.properties.onPropertyChange(table) { property ->
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
          DateFormatUtil.formatDateTime(DateFormatUtil.getSampleDateTime())
        }
      }
    )
  }

  override fun getStubValue(model: GraphTableModel): String = ""
}

internal object Hash : VcsLogDefaultColumn<String>("Default.Hash", VcsLogBundle.message("vcs.log.column.hash")), VcsLogMetadataColumn {
  override fun getValue(model: GraphTableModel, row: Int): String = getValue(model, model.getCommitMetadata(row, true))
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

@ApiStatus.Internal
interface VcsLogMetadataColumn {
  @ApiStatus.Internal
  fun getValue(model: GraphTableModel, commit: VcsCommitMetadata): @NlsSafe String
}
