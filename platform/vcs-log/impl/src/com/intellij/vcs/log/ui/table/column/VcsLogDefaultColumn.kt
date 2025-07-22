// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table.column

import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ExperimentalUI
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.text.DateTimeFormatManager
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.data.LoadingDetails
import com.intellij.vcs.log.data.roots
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
import com.intellij.vcs.log.ui.render.RootCell
import com.intellij.vcs.log.ui.table.*
import com.intellij.vcs.log.ui.table.links.CommitLinksResolveListener
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.*
import javax.swing.table.TableCellRenderer

internal fun getDefaultDynamicColumns() = listOf<VcsLogDefaultColumn<*>>(Author, Hash, Date)

@ApiStatus.Internal
sealed class VcsLogDefaultColumn<T>(
  @NonNls override val id: String,
  override val localizedName: @Nls String,
  override val isDynamic: Boolean = true,
) : VcsLogColumn<T> {
  /**
   * @return stable name (to identify column in statistics)
   */
  val stableName: String
    get() = id.lowercase(Locale.ROOT)
}

@ApiStatus.Internal
data object Root : VcsLogDefaultColumn<RootCell>("Default.Root", "", false) {
  override val isResizable: Boolean = false

  override fun getValue(model: GraphTableModel, row: VcsLogTableIndex): RootCell? {
    val visiblePack = model.visiblePack
    if (visiblePack.hasPathsInformation()) {
      val commit = model.getId(row) ?: return null
      val path = visiblePack.filePathOrDefault(commit)
      if (path != null) {
        return RootCell.RealCommit(path)
      }
    }
    return RootCell.RealCommit(model.getRootAtRow(row)?.let(VcsUtil::getFilePath))
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

  override fun getStubValue(model: GraphTableModel): RootCell = RootCell.RealCommit(VcsUtil.getFilePath(model.logData.roots.first()))
}

@ApiStatus.Internal
object Commit : VcsLogDefaultColumn<GraphCommitCell>("Default.Subject", VcsLogBundle.message("vcs.log.column.subject"), false),
                VcsLogMetadataColumn {
  override fun getValue(model: GraphTableModel, row: VcsLogTableIndex): GraphCommitCell? {
    val printElements = model.getPrintElements(row)
    val metadata = model.getCommitMetadata(row, true) ?: return null
    val commitId = metadata.getKnownCommitId()
    return GraphCommitCell.RealCommit(
      commitId,
      getValue(model, metadata),
      model.getRefsAtRow(row),
      if (metadata !is LoadingDetails) getBookmarkRefs(model.logData.project, metadata.id, metadata.root) else emptyList(),
      printElements,
      metadata is LoadingDetails
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

    table.logData.project.messageBus.connect(table).subscribe(CommitLinksResolveListener.TOPIC, CommitLinksResolveListener { logId->
      if (logId == table.id) {
        table.repaint()
      }
    })
    return commitCellRenderer
  }

  override fun getStubValue(model: GraphTableModel): GraphCommitCell {
    return GraphCommitCell.RealCommit(null, "", emptyList(), emptyList(), emptyList(), true)
  }

}

@ApiStatus.Internal
object Author : VcsLogDefaultColumn<String>("Default.Author", VcsLogBundle.message("vcs.log.column.author")),
                         VcsLogMetadataColumn {
  override fun getValue(model: GraphTableModel, row: VcsLogTableIndex): String? = model.getCommitMetadata(row, true)?.let { getValue(model, it) }
  override fun getValue(model: GraphTableModel, commit: VcsCommitMetadata): String = CommitPresentationUtil.getAuthorPresentation(commit)

  override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer {
    updateTableOnCommitDetailsLoaded(this, table)
    return VcsLogStringCellRenderer(true)
  }

  override fun getStubValue(model: GraphTableModel): String = ""
}

internal object Date : VcsLogDefaultColumn<String>("Default.Date", VcsLogBundle.message("vcs.log.column.date")), VcsLogMetadataColumn {
  override fun getValue(model: GraphTableModel, row: VcsLogTableIndex): String? {
    return model.getCommitMetadata(row, true)?.let { getValue(model, it) }
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
  override fun getValue(model: GraphTableModel, row: VcsLogTableIndex): String? = model.getCommitMetadata(row, true)?.let { getValue(model, it) }
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
