// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive.dialog

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ui.CommitIconTableCellRenderer
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.data.index.IndexedDetails
import com.intellij.vcs.log.paint.PaintParameters
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.interactive.dialog.GitRebaseCommitsTableView.Companion.DEFAULT_CELL_HEIGHT
import git4idea.rebase.interactive.dialog.GitRebaseCommitsTableView.Companion.GRAPH_COLOR
import git4idea.rebase.interactive.dialog.GitRebaseCommitsTableView.Companion.GRAPH_LINE_WIDTH
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*
import javax.swing.table.TableCellEditor

internal open class GitRebaseCommitsTableView(
  val project: Project,
  val model: GitRebaseCommitsTableModel,
  private val disposable: Disposable
) : JBTable(model) {

  companion object {
    const val DEFAULT_CELL_HEIGHT = PaintParameters.ROW_HEIGHT
    const val GRAPH_LINE_WIDTH = 1.5f
    val GRAPH_COLOR = JBColor.namedColor("VersionControl.GitCommits.graphColor", JBColor(Color(174, 185, 192), Color(135, 146, 154)))
  }

  init {
    setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
    columnModel.selectionModel = object : DefaultListSelectionModel() {
      override fun setSelectionInterval(index0: Int, index1: Int) {
        val indexToForce = this@GitRebaseCommitsTableView.convertColumnIndexToView(GitRebaseCommitsTableModel.SUBJECT_COLUMN)
        super.setSelectionInterval(indexToForce, indexToForce)
      }
    }
    intercellSpacing = JBUI.emptySize()
    tableHeader = null
    installSpeedSearch()
    prepareCommitIconColumn()
    prepareSubjectColumn()
  }

  final override fun setSelectionMode(selectionMode: Int) {
    super.setSelectionMode(selectionMode)
  }

  private fun installSpeedSearch() {
    TableSpeedSearch(this) { o, cell -> o.toString().takeIf { cell.column == GitRebaseCommitsTableModel.SUBJECT_COLUMN } }
  }

  private fun prepareCommitIconColumn() {
    val commitIconColumn = columnModel.getColumn(GitRebaseCommitsTableModel.COMMIT_ICON_COLUMN)
    commitIconColumn.cellRenderer = GitRebaseCommitIconTableCellRenderer()
    adjustCommitIconColumnWidth()
  }

  private fun adjustCommitIconColumnWidth() {
    val contentWidth = getExpandedColumnWidth(GitRebaseCommitsTableModel.COMMIT_ICON_COLUMN) + UIUtil.DEFAULT_HGAP
    val column = columnModel.getColumn(GitRebaseCommitsTableModel.COMMIT_ICON_COLUMN)
    column.maxWidth = contentWidth
    column.preferredWidth = contentWidth
  }

  override fun prepareEditor(editor: TableCellEditor?, row: Int, column: Int): Component {
    onEditorCreate()
    return super.prepareEditor(editor, row, column)
  }

  protected open fun onEditorCreate() {}

  override fun removeEditor() {
    onEditorRemove()
    if (editingRow in 0 until rowCount) {
      setRowHeight(editingRow, DEFAULT_CELL_HEIGHT)
    }
    super.removeEditor()
  }

  protected open fun onEditorRemove() {}

  private fun prepareSubjectColumn() {
    val subjectColumn = columnModel.getColumn(GitRebaseCommitsTableModel.SUBJECT_COLUMN)
    subjectColumn.cellRenderer = SubjectRenderer()
    subjectColumn.cellEditor = CommitMessageCellEditor(project, this, disposable)
  }

  internal fun getDrawNodeType(row: Int): NodeType = when {
    model.getEntryAction(row) == GitRebaseEntry.Action.EDIT -> NodeType.EDIT
    model.isFixupOrDrop(row) -> NodeType.NO_NODE
    model.isFixupRoot(row) -> NodeType.DOUBLE_NODE
    else -> NodeType.SIMPLE_NODE
  }
}

private class CommitMessageCellEditor(
  project: Project,
  private val table: GitRebaseCommitsTableView,
  disposable: Disposable
) : AbstractCellEditor(), TableCellEditor {
  private val closeEditorAction = object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      stopCellEditing()
    }
  }
  private val commitMessageField = object : CommitMessage(project, false, false, true) {
    override fun requestFocus() {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown { IdeFocusManager.getGlobalInstance().requestFocus(editorField, true) }
    }
  }.apply {
    editorField.addSettingsProvider { editor ->
      registerCloseEditorShortcut(editor, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK))
      registerCloseEditorShortcut(editor, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.META_DOWN_MASK))
    }
    editorField.setCaretPosition(0)
  }

  init {
    Disposer.register(disposable, commitMessageField)
  }

  private fun registerCloseEditorShortcut(editor: EditorEx, shortcut: KeyStroke) {
    val key = "applyEdit$shortcut"
    editor.contentComponent.inputMap.put(shortcut, key)
    editor.contentComponent.actionMap.put(key, closeEditorAction)
  }

  override fun getTableCellEditorComponent(table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
    val model = table.model as GitRebaseCommitsTableModel
    val entry = model.getEntry(row)
    commitMessageField.text = entry.newMessage
    table.setRowHeight(row, DEFAULT_CELL_HEIGHT * 5)
    return commitMessageField
  }

  override fun getCellEditorValue() = commitMessageField.text

  override fun isCellEditable(e: EventObject?) = when {
    table.selectedRowCount > 1 -> false
    e is MouseEvent -> e.clickCount >= 2
    else -> true
  }
}

private class SubjectRenderer : ColoredTableCellRenderer() {
  companion object {
    private const val GRAPH_WIDTH = 20
    private const val CONNECTION_CENTER_X = GRAPH_WIDTH / 4
  }

  private var graphType: GraphType = GraphType.NoGraph
  private var rowHeight: Int = DEFAULT_CELL_HEIGHT

  private val connectionCenterY: Int
    get() = rowHeight / 2

  override fun paint(g: Graphics?) {
    super.paint(g)
    (g as Graphics2D).paintFixupGraph()
  }

  private fun Graphics2D.paintFixupGraph() {
    when (val type = graphType) {
      is GraphType.NoGraph -> {
      }
      is GraphType.FixupGraph -> {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        color = GRAPH_COLOR
        stroke = BasicStroke(GRAPH_LINE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL)
        drawCenterLine()
        drawUpLine(type.isFirst)
        if (!type.isLast) {
          drawDownLine()
        }
      }
    }
  }

  private fun Graphics2D.drawCenterLine() {
    val gap = GRAPH_WIDTH / 5
    val xRight = GRAPH_WIDTH - gap
    drawLine(CONNECTION_CENTER_X, connectionCenterY, xRight, connectionCenterY)
  }

  private fun Graphics2D.drawDownLine() {
    drawLine(CONNECTION_CENTER_X, connectionCenterY, CONNECTION_CENTER_X, rowHeight)
  }

  private fun Graphics2D.drawUpLine(withArrow: Boolean) {
    val triangleSide = JBUI.scale(8)
    val triangleBottomY = triangleSide / 2
    val triangleBottomXDiff = triangleSide / 2
    val upLineY = if (withArrow) triangleBottomY else 0
    drawLine(CONNECTION_CENTER_X, connectionCenterY, CONNECTION_CENTER_X, upLineY)

    if (withArrow) {
      val xPoints = intArrayOf(CONNECTION_CENTER_X, CONNECTION_CENTER_X - triangleBottomXDiff, CONNECTION_CENTER_X + triangleBottomXDiff)
      val yPoints = intArrayOf(0, triangleBottomY, triangleBottomY)
      fillPolygon(xPoints, yPoints, xPoints.size)
    }
  }

  private fun getRowGraphType(table: GitRebaseCommitsTableView, row: Int) =
    if (table.model.getEntryAction(row) == GitRebaseEntry.Action.FIXUP) {
      GraphType.FixupGraph(table.model.isFirstFixup(row), table.model.isLastFixup(row))
    }
    else {
      GraphType.NoGraph
    }

  override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
    if (value != null) {
      border = null
      isOpaque = false
      val commitsTable = table as GitRebaseCommitsTableView
      graphType = getRowGraphType(commitsTable, row)
      rowHeight = table.getRowHeight(row)
      val entryWithEditedMessage = commitsTable.model.getEntry(row)
      var attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES
      when (entryWithEditedMessage.entry.action) {
        GitRebaseEntry.Action.DROP -> {
          attributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, null)
        }
        GitRebaseEntry.Action.REWORD -> {
          attributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.BLUE)
        }
        GitRebaseEntry.Action.FIXUP -> {
          append("")
          appendTextPadding(GRAPH_WIDTH)
        }
        else -> {
        }
      }
      append(IndexedDetails.getSubject(entryWithEditedMessage.newMessage), attributes, true)
      SpeedSearchUtil.applySpeedSearchHighlighting(table, this, true, selected)
    }
  }

  private sealed class GraphType {
    object NoGraph : GraphType()
    class FixupGraph(val isFirst: Boolean, val isLast: Boolean) : GraphType()
  }
}

private class GitRebaseCommitIconTableCellRenderer : CommitIconTableCellRenderer({ GRAPH_COLOR }, DEFAULT_CELL_HEIGHT, GRAPH_LINE_WIDTH) {
  companion object {
    private const val NODE_WIDTH = 8
    private const val NODE_CENTER_X = NODE_WIDTH
    private const val NODE_CENTER_Y = DEFAULT_CELL_HEIGHT / 2
  }

  private var isHead = false
  private var nodeType = NodeType.SIMPLE_NODE

  override fun customizeRenderer(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
    if (table != null && table is GitRebaseCommitsTableView) {
      nodeType = table.getDrawNodeType(row)
      isHead = row == table.rowCount - 1
    }
  }

  override fun drawCommitIcon(g: Graphics2D) {
    val tableRowHeight = rowHeight
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    when (nodeType) {
      NodeType.SIMPLE_NODE -> {
        drawNode(g)
      }
      NodeType.DOUBLE_NODE -> {
        drawDoubleNode(g)
      }
      NodeType.NO_NODE -> {
      }
      NodeType.EDIT -> {
        drawEditNode(g)
      }
    }
    if (nodeType != NodeType.EDIT) {
      drawEdge(g, tableRowHeight, false)
      if (!isHead) {
        drawEdge(g, tableRowHeight, true)
      }
    }
  }

  private fun drawDoubleNode(g: Graphics2D) {
    val circleRadius = NODE_WIDTH / 2
    val backgroundCircleRadius = circleRadius + 1
    val leftCircleX0 = NODE_CENTER_X
    val y0 = NODE_CENTER_Y
    val rightCircleX0 = leftCircleX0 + circleRadius

    // right circle
    drawCircle(g, rightCircleX0, y0)

    // distance between circles
    drawCircle(g, leftCircleX0, y0, backgroundCircleRadius, background)

    // left circle
    drawCircle(g, leftCircleX0, y0)
  }

  private fun drawEditNode(g: Graphics2D) {
    val icon = AllIcons.Actions.Pause
    icon.paintIcon(null, g, NODE_CENTER_X - icon.iconWidth / 2, NODE_CENTER_Y - icon.iconHeight / 2)
  }

}

internal enum class NodeType {
  NO_NODE, SIMPLE_NODE, DOUBLE_NODE, EDIT
}
