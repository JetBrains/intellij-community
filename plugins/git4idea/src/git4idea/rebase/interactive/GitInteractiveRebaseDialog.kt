// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.EditableModel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.graph.DefaultColorGenerator
import com.intellij.vcs.log.graph.EdgePrintElement
import com.intellij.vcs.log.graph.NodePrintElement
import com.intellij.vcs.log.graph.PrintElement
import com.intellij.vcs.log.paint.ColorGenerator
import com.intellij.vcs.log.paint.SimpleGraphCellPainter
import com.intellij.vcs.log.ui.details.FullCommitDetailsListPanel
import git4idea.history.GitCommitRequirements
import git4idea.history.GitLogUtil
import git4idea.i18n.GitBundle
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.GitRebaseEntryWithDetails
import git4idea.rebase.interactive.CommitsTableModel.Companion.SUBJECT_COLUMN
import org.jetbrains.annotations.CalledInBackground
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellRenderer
import kotlin.math.max
import kotlin.math.min

internal class GitInteractiveRebaseDialog(
  project: Project,
  root: VirtualFile,
  entries: List<GitRebaseEntryWithDetails>
) : DialogWrapper(project, true) {
  companion object {
    private const val DETAILS_PROPORTION = "Git.Interactive.Rebase.Details.Proportion"
    private const val DIMENSION_KEY = "Git.Interactive.Rebase.Dialog"

    private const val DIALOG_HEIGHT = 450
    private const val DIALOG_WIDTH = 800
  }

  private val commitsTableModel = CommitsTableModel(entries)
  private val commitsTable = CommitsTable(commitsTableModel)
  private val modalityState = ModalityState.stateForComponent(window)
  private val fullCommitDetailsListPanel = object : FullCommitDetailsListPanel(project, disposable, modalityState) {
    @CalledInBackground
    @Throws(VcsException::class)
    override fun loadChanges(commits: List<VcsCommitMetadata>): List<Change> {
      val changes = mutableListOf<Change>()
      GitLogUtil.readFullDetailsForHashes(project, root, commits.map { it.id.asString() }, GitCommitRequirements.DEFAULT) { gitCommit ->
        changes.addAll(gitCommit.changes)
      }
      return CommittedChangesTreeBrowser.zipChanges(changes)
    }
  }

  init {
    commitsTable.selectionModel.addListSelectionListener { e ->
      if (!e.valueIsAdjusting) {
        fullCommitDetailsListPanel.commitsSelected(commitsTable.selectedRows.map { commitsTableModel.entries[it].commitDetails })
      }
    }

    title = GitBundle.getString("rebase.editor.title")
    setOKButtonText(GitBundle.getString("rebase.editor.button"))
    init()
  }

  override fun getDimensionServiceKey() = DIMENSION_KEY

  override fun createCenterPanel() = BorderLayoutPanel().apply {
    val tablePanel = ToolbarDecorator.createDecorator(commitsTable)
      .setAsUsualTopToolbar()
      .setPanelBorder(IdeBorderFactory.createBorder(SideBorder.TOP))
      .disableAddAction()
      .disableRemoveAction()
      .createPanel()
      .apply {
        border = JBUI.Borders.emptyTop(4)
      }

    val detailsSplitter = OnePixelSplitter(DETAILS_PROPORTION, 0.5f).apply {
      firstComponent = tablePanel
      secondComponent = fullCommitDetailsListPanel
    }
    addToCenter(detailsSplitter)
    preferredSize = JBDimension(DIALOG_WIDTH, DIALOG_HEIGHT)
  }

  override fun getStyle() = DialogStyle.COMPACT

  fun getEntries(): List<GitRebaseEntry> = commitsTableModel.entries

  override fun getPreferredFocusedComponent(): JComponent = commitsTable
}

private class CommitsTableModel(initialEntries: List<GitRebaseEntryWithDetails>) : AbstractTableModel(), EditableModel {
  companion object {
    const val COMMIT_ICON_COLUMN = 0
    const val SUBJECT_COLUMN = 1
  }

  private val _entries: MutableList<GitRebaseEntryWithDetails> = initialEntries.toMutableList()
  val entries: List<GitRebaseEntryWithDetails> = _entries

  override fun getRowCount() = _entries.size

  override fun getColumnCount() = SUBJECT_COLUMN + 1

  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = when (columnIndex) {
    COMMIT_ICON_COLUMN -> _entries[rowIndex]
    SUBJECT_COLUMN -> _entries[rowIndex].subject
    else -> throw IllegalArgumentException("Unsupported column index: $columnIndex")
  }

  override fun exchangeRows(oldIndex: Int, newIndex: Int) {
    val movingElement: GitRebaseEntryWithDetails = _entries.removeAt(oldIndex)
    _entries.add(newIndex, movingElement)
    fireTableRowsUpdated(min(oldIndex, newIndex), max(oldIndex, newIndex))
  }

  override fun canExchangeRows(oldIndex: Int, newIndex: Int) = true

  override fun removeRow(idx: Int) {
    throw UnsupportedOperationException()
  }

  override fun addRow() {
    throw UnsupportedOperationException()
  }
}

private class CommitsTable(val model: CommitsTableModel) : JBTable(model) {
  init {
    setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
    intercellSpacing = JBUI.emptySize()
    tableHeader = null
    installSpeedSearch()
    prepareCommitIconColumn()
    prepareSubjectColumn()
  }

  private fun installSpeedSearch() {
    TableSpeedSearch(this) { o, cell -> o.toString().takeIf { cell.column == SUBJECT_COLUMN } }
  }

  private fun prepareCommitIconColumn() {
    val commitIconColumn = columnModel.getColumn(CommitsTableModel.COMMIT_ICON_COLUMN)
    val renderer = CommitIconRenderer()
    commitIconColumn.cellRenderer = TableCellRenderer { table, _, isSelected, hasFocus, row, column ->
      renderer.update(table, isSelected, hasFocus, row, column, row == table.rowCount - 1)
      renderer
    }
    adjustCommitIconColumnWidth()
  }

  private fun adjustCommitIconColumnWidth() {
    val contentWidth = getExpandedColumnWidth(CommitsTableModel.COMMIT_ICON_COLUMN) + UIUtil.DEFAULT_HGAP
    val column = columnModel.getColumn(CommitsTableModel.COMMIT_ICON_COLUMN)
    column.maxWidth = contentWidth
    column.preferredWidth = contentWidth
  }

  private fun prepareSubjectColumn() {
    val subjectColumn = columnModel.getColumn(SUBJECT_COLUMN)
    subjectColumn.cellRenderer = object : ColoredTableCellRenderer() {
      override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
        if (value != null) {
          border = null
          append(value.toString())
          SpeedSearchUtil.applySpeedSearchHighlighting(table, this, true, selected)
        }
      }
    }
  }
}

private class CommitIconRenderer : SimpleColoredRenderer() {
  companion object {
    private val UP_EDGE = getEdge(EdgePrintElement.Type.UP)
    private val DOWN_EDGE = getEdge(EdgePrintElement.Type.DOWN)
    private val NODE = object : NodePrintElement {
      override fun getRowIndex(): Int = 0
      override fun getPositionInCurrentRow(): Int = 0
      override fun getColorId(): Int = 0
      override fun isSelected(): Boolean = false
    }

    private fun getEdge(type: EdgePrintElement.Type) = object : EdgePrintElement {
      override fun getPositionInOtherRow(): Int = 0
      override fun getType(): EdgePrintElement.Type = type
      override fun getLineStyle(): EdgePrintElement.LineStyle = EdgePrintElement.LineStyle.SOLID
      override fun hasArrow(): Boolean = false
      override fun getRowIndex(): Int = 0
      override fun getPositionInCurrentRow(): Int = 0
      override fun getColorId(): Int = 0
      override fun isSelected(): Boolean = false
    }
  }

  private val nodeColor = DefaultColorGenerator().getColor(1)
  private val painter = SimpleGraphCellPainter(ColorGenerator { nodeColor })
  private var isHead = false

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    drawCommitIcon(g as Graphics2D)
  }

  fun update(table: JTable?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int, isHead: Boolean) {
    clear()
    setPaintFocusBorder(false)
    acquireState(table, isSelected, hasFocus, row, column)
    cellState.updateRenderer(this)
    border = null
    this.isHead = isHead
  }

  private fun drawCommitIcon(g2: Graphics2D) {
    val elements = mutableListOf<PrintElement>(UP_EDGE)
    elements.add(NODE)
    if (!isHead) {
      elements.add(DOWN_EDGE)
    }
    painter.draw(g2, elements)
  }
}
