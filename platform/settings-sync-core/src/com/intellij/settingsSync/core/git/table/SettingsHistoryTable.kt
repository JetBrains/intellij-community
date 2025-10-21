package com.intellij.settingsSync.core.git.table

import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.ListSelection
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.settingsSync.core.SettingsSyncBundle
import com.intellij.settingsSync.core.SettingsSyncEvents
import com.intellij.settingsSync.core.SyncSettingsEvent
import com.intellij.settingsSync.core.git.record.HistoryRecord
import com.intellij.settingsSync.core.git.renderers.*
import com.intellij.ui.SingleSelectionModel
import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.data.roots
import com.intellij.vcs.log.ui.table.CommitSelectionImpl
import com.intellij.vcs.log.ui.table.VcsLogCommitList
import com.intellij.vcs.log.ui.table.VcsLogCommitListModel
import git4idea.repo.GitRepositoryManager
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.table.TableCellRenderer

internal class SettingsHistoryTable(private val tableModel: SettingsHistoryTableModel, private val project: Project) :
  JBTable(tableModel), VcsLogCommitList {

  companion object {
    val logger = logger<SettingsHistoryTable>()
  }

  private val nodeRenderer = SettingsHistoryNodeCellRenderer()
  private val titleRenderer = SettingsHistoryTitleCellRenderer()
  private val subtitleRenderer = SettingsHistorySubtitleRenderer()
  private val fileRenderer = SettingsHistoryFileCellRenderer()
  private val restoreRenderer = SettingsHistoryRestoreCellRenderer()
  private val emptyRenderer = SettingsHistoryEmptyCellRenderer()

  var isResetHovered: Boolean = false
  val hoveredRow: Int
    get() = TableHoverListener.getHoveredRow(this)
  var hoveredRecord: HistoryRecord? = null

  init {
    autoResizeMode = AUTO_RESIZE_LAST_COLUMN
    intercellSpacing = JBUI.emptySize()
    showVerticalLines = false
    showHorizontalLines = false

    setExactWidthForColumn(0, 32)
    setExactWidthForColumn(2, 34)

    selectionModel = SingleSelectionModel()
    putClientProperty(RenderingUtil.PAINT_HOVERED_BACKGROUND, false)

    addMouseListener(MouseActionHandler())
    addMouseMotionListener(MouseMotionHandler())
  }

  private fun setExactWidthForColumn(columnIndex: Int, width: Int) {
    columnModel.getColumn(columnIndex).apply {
      preferredWidth = width
      maxWidth = width
      minWidth = width
    }
  }

  override fun getModel(): SettingsHistoryTableModel {
    return super.getModel() as SettingsHistoryTableModel
  }

  override fun getCellRenderer(row: Int, column: Int): TableCellRenderer {
    if (column == 0) {
      return nodeRenderer
    }

    val rowEntity = getValueAt(row, column)
    if (column == 1) {
      // It's done only for one column because it affects the whole row and there is no need to do it for each column
      if (rowEntity is SeparatorRow) {
        if (getRowHeight(row) != 16) setRowHeight(row, 16)
      }
      else {
        if (getRowHeight(row) != 24) setRowHeight(row, 24)
      }

      return when (rowEntity) {
        is TitleRow -> titleRenderer
        is SubtitleRow -> subtitleRenderer
        is FileRow -> fileRenderer
        is SeparatorRow -> emptyRenderer
      }
    }

    if (column == 2) {
      return if (rowEntity is TitleRow) {
        return restoreRenderer
      }
      else {
        emptyRenderer
      }
    }

    return emptyRenderer
  }

  override fun changeSelection(rowIndex: Int, columnIndex: Int, toggle: Boolean, extend: Boolean) {
    val row = getValueAt(rowIndex, 0)
    if (row !is SeparatorRow && row !is SubtitleRow) {
      super.changeSelection(rowIndex, columnIndex, toggle, extend)
    }
  }

  override fun getValueAt(row: Int, column: Int): SettingsHistoryTableRow {
    return super.getValueAt(row, column) as SettingsHistoryTableRow
  }

  override val selection: VcsLogCommitSelection
    get() {
      val visibleGraphRows = selectionModel.selectedIndices.map {
        tableModel.visiblePack.visibleGraph.getVisibleRowIndex(getRecordAtRow(it).commitId)
      }.filterNotNull().distinct().toIntArray()
      return CommitSelectionImpl(tableModel.logData, tableModel.visiblePack.visibleGraph, visibleGraphRows)
    }

  override val listModel: VcsLogCommitListModel get() = tableModel

  private fun getRecordAtRow(rowIndex: Int): HistoryRecord {
    return getValueAt(rowIndex, 0).record
  }

  private fun performRevertAtRow(rowIndex: Int) {
    fun getNextDistinctRecord(rowIndex: Int): HistoryRecord? {
      val currentRecord = getRecordAtRow(rowIndex)
      return (rowIndex + 1 until rowCount)
        .asSequence()
        .map { getRecordAtRow(it) }
        .firstOrNull { it != currentRecord }
    }

    val recordToRestore = if (rowIndex == 0) getNextDistinctRecord(0) else getRecordAtRow(rowIndex)
    if (recordToRestore == null) {
      logger.warn("Failed to get record to restore. Row index = $rowIndex")
      return
    }

    val commitHash = recordToRestore.id.asString()
    SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.RestoreSettingsSnapshot(commitHash) {
      // Here we update repository for it to fetch the newly appeared restore commit
      val repositoryManager = GitRepositoryManager.getInstance(project)
      getRoots().forEach { repositoryManager.updateRepository(it) }
    })
  }

  private fun getRoots(): Collection<VirtualFile> {
    return model.logData.roots
  }

  private fun isMouseOverRevertButton(rowIndex: Int, e: MouseEvent): Boolean {
    val row = getValueAt(rowIndex, 0)
    return row is TitleRow && columnAtPoint(e.point) == 2
  }


  private fun openChange(change: Change) {
    val showDiffContext = ShowDiffContext()
      .apply {
        putChangeContext(change, DiffUserDataKeysEx.VCS_DIFF_EDITOR_TAB_TITLE,
                         SettingsSyncBundle.message("ui.toolwindow.editor.diff.tab.title", ChangesUtil.getFilePath(change).name))
      }
    ShowDiffAction.showDiffForChange(project, ListSelection.create(listOf(change), change), showDiffContext)
  }

  private inner class MouseActionHandler : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent?) {
      e?.let {
        val rowIndex = rowAtPoint(it.point)
        if (rowIndex != -1) handleRowClick(rowIndex, it)
      }
    }

    override fun mouseEntered(e: MouseEvent?) {
      e?.let { repaint() }
    }

    override fun mouseExited(e: MouseEvent?) {
      e?.let { repaint() }
    }

    private fun handleRowClick(rowIndex: Int, e: MouseEvent) {
      when (val row = getValueAt(rowIndex, 0)) {
        is TitleRow -> handleTitleRowClick(row, rowIndex, e)
        is FileRow -> openChange(row.change)
        else -> {}
      }
      repaint()
    }

    private fun handleTitleRowClick(row: SettingsHistoryTableRow, rowIndex: Int, e: MouseEvent) {
      val isMouseOverRevert = columnAtPoint(e.point) == 2
      if (isMouseOverRevert) {
        performRevertAtRow(rowIndex)
      }
      else {
        model.toggleRowExpanding(row as TitleRow)
      }
    }
  }

  private inner class MouseMotionHandler : MouseAdapter() {
    override fun mouseMoved(e: MouseEvent?) {
      e?.let {
        val rowIndex = rowAtPoint(it.point)

        val oldIsResetHovered = isResetHovered
        isResetHovered = rowIndex != -1 && isMouseOverRevertButton(rowIndex, it)

        val oldHoveredRecord = hoveredRecord
        hoveredRecord = getHoveredRecord()

        if (oldIsResetHovered != isResetHovered || oldHoveredRecord != hoveredRecord) {
          repaint()
        }
      }
    }

    private fun getHoveredRecord(): HistoryRecord? {
      val hoveredRowIndex = hoveredRow
      if (hoveredRowIndex == -1) return null

      val row = getValueAt(hoveredRowIndex, 0)
      if (row is SeparatorRow) return null

      return row.record
    }
  }
}
