// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive.dialog

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ui.CommitIconTableCellRenderer
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.data.index.IndexedDetails
import com.intellij.vcs.log.paint.PaintParameters
import git4idea.rebase.interactive.GitRebaseTodoModel
import git4idea.rebase.interactive.dialog.GitRebaseCommitsTableView.Companion.DEFAULT_CELL_HEIGHT
import git4idea.rebase.interactive.dialog.GitRebaseCommitsTableView.Companion.GRAPH_COLOR
import git4idea.rebase.interactive.dialog.GitRebaseCommitsTableView.Companion.GRAPH_LINE_WIDTH
import git4idea.rebase.interactive.dialog.GitRebaseCommitsTableView.Companion.GRAPH_NODE_WIDTH
import git4idea.rebase.interactive.dialog.view.CommitMessageCellEditor
import java.awt.*
import javax.swing.DefaultListSelectionModel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.event.ChangeEvent
import javax.swing.table.TableCellEditor

internal open class GitRebaseCommitsTableView(
  val project: Project,
  val model: GitRebaseCommitsTableModel<*>,
  private val disposable: Disposable
) : JBTable(model) {

  companion object {
    val GRAPH_LINE_WIDTH: Float
      get() = JBUIScale.scale(1.5f)
    val GRAPH_NODE_WIDTH: Int
      get() = JBUI.scale(8)
    val DEFAULT_CELL_HEIGHT: Int
      get() = JBUI.scale(PaintParameters.ROW_HEIGHT)
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
    installUndoRedoActions()
  }

  private fun installUndoRedoActions() {
    installAnActionWithShortcut(UndoAction(this), IdeActions.ACTION_UNDO)
    installAnActionWithShortcut(RedoAction(this), IdeActions.ACTION_REDO)
  }

  private fun installAnActionWithShortcut(action: AnAction, shortcutActionId: String) {
    action.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts(shortcutActionId), this)
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
    val contentWidth = 2 * GRAPH_NODE_WIDTH + UIUtil.DEFAULT_HGAP
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

  override fun columnMarginChanged(e: ChangeEvent) {
    // same as JTable#columnMarginChanged except for it doesn't stop editing
    val tableHeader = getTableHeader()
    val resizingColumn = tableHeader?.resizingColumn
    if (resizingColumn != null && autoResizeMode == JTable.AUTO_RESIZE_OFF) {
      resizingColumn.preferredWidth = resizingColumn.width
    }
    resizeAndRepaint()
  }

  protected open fun onEditorRemove() {}

  private fun prepareSubjectColumn() {
    val subjectColumn = columnModel.getColumn(GitRebaseCommitsTableModel.SUBJECT_COLUMN)
    subjectColumn.cellRenderer = SubjectRenderer()
    subjectColumn.cellEditor = CommitMessageCellEditor(project, this, disposable)
  }

  internal fun getDrawNodeType(row: Int): NodeType = when {
    model.getElement(row).type == GitRebaseTodoModel.Type.NonUnite.KeepCommit.Edit -> NodeType.EDIT
    model.getElement(row).type !is GitRebaseTodoModel.Type.NonUnite.KeepCommit -> NodeType.NO_NODE
    model.getElement(row) is GitRebaseTodoModel.Element.UniteRoot -> NodeType.DOUBLE_NODE
    else -> NodeType.SIMPLE_NODE
  }

  private abstract class DisabledDuringRewordAction(protected val table: GitRebaseCommitsTableView) : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      val presentation = e.presentation
      presentation.isEnabledAndVisible = true
      if (table.editingRow != -1) {
        presentation.isEnabledAndVisible = false
      }
    }
  }

  private class UndoAction(table: GitRebaseCommitsTableView) : DisabledDuringRewordAction(table) {
    override fun actionPerformed(e: AnActionEvent) {
      table.model.undo()
    }
  }

  private class RedoAction(table: GitRebaseCommitsTableView) : DisabledDuringRewordAction(table) {
    override fun actionPerformed(e: AnActionEvent) {
      table.model.redo()
    }
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

  private fun getRowGraphType(table: GitRebaseCommitsTableView, row: Int): GraphType {
    val element = table.model.getElement(row)
    return if (element is GitRebaseTodoModel.Element.UniteChild<*>) {
      GraphType.FixupGraph(table.model.isFirstFixup(element), table.model.isLastFixup(element))
    }
    else {
      GraphType.NoGraph
    }
  }

  override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
    if (value != null) {
      border = null
      isOpaque = false
      val commitsTable = table as GitRebaseCommitsTableView
      graphType = getRowGraphType(commitsTable, row)
      rowHeight = table.getRowHeight(row)
      var attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES
      val element = commitsTable.model.rebaseTodoModel.elements[row]
      when (element.type) {
        GitRebaseTodoModel.Type.NonUnite.Drop -> {
          attributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, null)
        }
        is GitRebaseTodoModel.Type.NonUnite.KeepCommit.Reword -> {
          attributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.BLUE)
        }
        GitRebaseTodoModel.Type.Unite -> {
          append("")
          appendTextPadding(GRAPH_WIDTH)
        }
        else -> {
        }
      }
      append(IndexedDetails.getSubject(commitsTable.model.getCommitMessage(row)), attributes, true)
      SpeedSearchUtil.applySpeedSearchHighlighting(table, this, true, selected)
    }
  }

  private sealed class GraphType {
    object NoGraph : GraphType()
    class FixupGraph(val isFirst: Boolean, val isLast: Boolean) : GraphType()
  }
}

private class GitRebaseCommitIconTableCellRenderer : CommitIconTableCellRenderer({ GRAPH_COLOR }, DEFAULT_CELL_HEIGHT, GRAPH_LINE_WIDTH) {
  override val nodeWidth: Int
    get() = GRAPH_NODE_WIDTH
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
    val circleRadius = nodeWidth / 2
    val backgroundCircleRadius = circleRadius + 1
    val leftCircleX0 = nodeCenterX
    val y0 = nodeCenterY
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
    icon.paintIcon(null, g, nodeCenterX - icon.iconWidth / 2, nodeCenterY - icon.iconHeight / 2)
  }

}

internal enum class NodeType {
  NO_NODE, SIMPLE_NODE, DOUBLE_NODE, EDIT
}
