// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive.dialog

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonPainter
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.*
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.EditableModel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.index.IndexedDetails.Companion.getSubject
import com.intellij.vcs.log.graph.DefaultColorGenerator
import com.intellij.vcs.log.paint.PaintParameters
import com.intellij.vcs.log.ui.details.FullCommitDetailsListPanel
import git4idea.history.GitCommitRequirements
import git4idea.history.GitLogUtil
import git4idea.i18n.GitBundle
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.GitRebaseEntryWithDetails
import git4idea.rebase.interactive.dialog.CommitsTable.Companion.DEFAULT_CELL_HEIGHT
import git4idea.rebase.interactive.dialog.CommitsTable.Companion.GRAPH_COLOR
import git4idea.rebase.interactive.dialog.CommitsTable.Companion.GRAPH_LINE_WIDTH
import git4idea.rebase.interactive.dialog.CommitsTableModel.Companion.SUBJECT_COLUMN
import org.jetbrains.annotations.CalledInBackground
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.util.*
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import kotlin.math.max
import kotlin.math.min

internal class GitInteractiveRebaseDialog(
  private val project: Project,
  root: VirtualFile,
  entries: List<GitRebaseEntryWithDetails>
) : DialogWrapper(project, true) {
  companion object {
    private const val DETAILS_PROPORTION = "Git.Interactive.Rebase.Details.Proportion"
    private const val DIMENSION_KEY = "Git.Interactive.Rebase.Dialog"
    internal const val PLACE = "Git.Interactive.Rebase.Dialog"

    private const val DIALOG_HEIGHT = 450
    private const val DIALOG_WIDTH = 800
  }

  private val commitsTableModel = CommitsTableModel(entries.map {
    GitRebaseEntryWithEditedMessage(
      GitRebaseEntryWithDetails(GitRebaseEntry(it.action, it.commit, it.subject), it.commitDetails)
    )
  })
  private val resetEntriesLabel = LinkLabel<Any?>(GitBundle.getString("rebase.interactive.dialog.reset.link.text"), null).apply {
    isVisible = false
    setListener(
      LinkListener { _, _ ->
        commitsTable.removeEditor()
        commitsTableModel.resetEntries()
        isVisible = false
      },
      null
    )
  }
  private val commitsTable = object : CommitsTable(project, commitsTableModel, disposable) {
    override fun onEditorCreate() {
      isOKActionEnabled = false
    }

    override fun onEditorRemove() {
      isOKActionEnabled = true
    }
  }
  private val modalityState = window?.let { ModalityState.stateForComponent(it) } ?: ModalityState.current()
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
  private val pickAction = ChangeEntryStateSimpleAction(GitRebaseEntry.Action.PICK, AllIcons.Actions.Rollback, commitsTable)
  private val actions = listOf<AnActionButton>(
    RewordAction(commitsTable),
    FixupAction(commitsTable),
    ChangeEntryStateButtonAction(GitRebaseEntry.Action.DROP, commitsTable)
  )
  private val contextMenuOnlyActions = listOf<AnAction>(
    ChangeEntryStateSimpleAction(
      GitRebaseEntry.Action.EDIT,
      GitBundle.getString("rebase.interactive.dialog.stop.to.edit.text"),
      GitBundle.getString("rebase.interactive.dialog.stop.to.edit.text"),
      null,
      commitsTable
    ),
    Separator.getInstance(),
    ShowGitRebaseEditorLikeEntriesAction(project, commitsTable)
  )

  init {
    commitsTable.selectionModel.addListSelectionListener { e ->
      if (!e.valueIsAdjusting) {
        fullCommitDetailsListPanel.commitsSelected(commitsTable.selectedRows.map { commitsTableModel.getEntry(it).entry.commitDetails })
      }
    }
    commitsTableModel.addTableModelListener { resetEntriesLabel.isVisible = true }
    PopupHandler.installRowSelectionTablePopup(
      commitsTable,
      DefaultActionGroup().apply {
        add(pickAction)
        addAll(actions)
        addSeparator()
        addAll(contextMenuOnlyActions)
      },
      PLACE,
      ActionManager.getInstance()
    )

    title = GitBundle.getString("rebase.editor.title")
    setOKButtonText(GitBundle.getString("rebase.editor.button"))
    init()
  }

  override fun getDimensionServiceKey() = DIMENSION_KEY

  override fun createCenterPanel() = BorderLayoutPanel().apply {
    val decorator = ToolbarDecorator.createDecorator(commitsTable)
      .setAsUsualTopToolbar()
      .setPanelBorder(IdeBorderFactory.createBorder(SideBorder.TOP))
      .disableAddAction()
      .disableRemoveAction()
      .addExtraAction(pickAction)
      .addExtraAction(AnActionButtonSeparator())
    actions.forEach {
      decorator.addExtraAction(it)
    }

    val tablePanel = decorator.createPanel()
    val resetEntriesLabelPanel = BorderLayoutPanel().addToCenter(resetEntriesLabel).apply {
      border = JBUI.Borders.emptyRight(10)
    }
    decorator.actionsPanel.apply {
      add(BorderLayout.EAST, resetEntriesLabelPanel)
    }

    val detailsSplitter = OnePixelSplitter(DETAILS_PROPORTION, 0.5f).apply {
      firstComponent = tablePanel
      secondComponent = fullCommitDetailsListPanel
    }
    addToCenter(detailsSplitter)
    preferredSize = JBDimension(DIALOG_WIDTH, DIALOG_HEIGHT)
  }

  override fun getStyle() = DialogStyle.COMPACT

  fun getEntries(): List<GitRebaseEntryWithEditedMessage> = commitsTableModel.entries

  override fun getPreferredFocusedComponent(): JComponent = commitsTable
}

private class CommitsTableModel(initialEntries: List<GitRebaseEntryWithEditedMessage>) : AbstractTableModel(), EditableModel {
  companion object {
    const val COMMIT_ICON_COLUMN = 0
    const val SUBJECT_COLUMN = 1
  }

  private val rows: MutableList<CommitTableModelRow> = initialEntries.mapIndexed { i, entry ->
    CommitTableModelRow(i, entry)
  }.toMutableList()

  val entries: List<GitRebaseEntryWithEditedMessage>
    get() = rows.map { it.entry }

  fun resetEntries() {
    rows.sortBy { it.initialIndex }
    rows.forEach {
      it.action = it.initialAction
      it.newMessage = it.entry.entry.commitDetails.fullMessage
    }
    fireTableRowsUpdated(0, rows.size - 1)
  }

  override fun getRowCount() = rows.size

  override fun getColumnCount() = SUBJECT_COLUMN + 1

  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = when (columnIndex) {
    COMMIT_ICON_COLUMN -> rows[rowIndex]
    SUBJECT_COLUMN -> rows[rowIndex].newMessage
    else -> throw IllegalArgumentException("Unsupported column index: $columnIndex")
  }

  override fun exchangeRows(oldIndex: Int, newIndex: Int) {
    val movingElement = rows.removeAt(oldIndex)
    rows.add(newIndex, movingElement)
    fireTableRowsUpdated(min(oldIndex, newIndex), max(oldIndex, newIndex))
  }

  override fun canExchangeRows(oldIndex: Int, newIndex: Int) = true

  override fun removeRow(idx: Int) {
    throw UnsupportedOperationException()
  }

  override fun addRow() {
    throw UnsupportedOperationException()
  }

  override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
    when (aValue) {
      is GitRebaseEntry.Action -> {
        val row = rows[rowIndex]
        if (aValue != GitRebaseEntry.Action.REWORD) {
          row.newMessage = row.details.fullMessage
        }
        row.action = aValue
      }
      is String -> {
        rows[rowIndex].action =
          if (rows[rowIndex].details.fullMessage != aValue) {
            GitRebaseEntry.Action.REWORD
          }
          else {
            GitRebaseEntry.Action.PICK
          }
        rows[rowIndex].newMessage = aValue
      }
      else -> throw IllegalArgumentException()
    }
    fireTableRowsUpdated(rowIndex, rowIndex)
  }

  override fun isCellEditable(rowIndex: Int, columnIndex: Int) = true

  fun getEntry(row: Int): GitRebaseEntryWithEditedMessage = rows[row].entry

  private class CommitTableModelRow(val initialIndex: Int, val entry: GitRebaseEntryWithEditedMessage) {
    val initialAction = entry.entry.action
    val details = entry.entry.commitDetails
    var action
      get() = entry.entry.action
      set(value) {
        entry.entry.action = value
      }
    var newMessage
      get() = entry.newMessage
      set(value) {
        entry.newMessage = value
      }
  }
}

private open class CommitsTable(val project: Project, val model: CommitsTableModel, private val disposable: Disposable) : JBTable(model) {
  companion object {
    const val DEFAULT_CELL_HEIGHT = PaintParameters.ROW_HEIGHT
    const val GRAPH_LINE_WIDTH = 1.5f
    val GRAPH_COLOR = DefaultColorGenerator().getColor(1)
  }

  init {
    setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
    columnModel.selectionModel = object : DefaultListSelectionModel() {
      override fun setSelectionInterval(index0: Int, index1: Int) {
        val indexToForce = this@CommitsTable.convertColumnIndexToView(SUBJECT_COLUMN)
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
    TableSpeedSearch(this) { o, cell -> o.toString().takeIf { cell.column == SUBJECT_COLUMN } }
  }

  private fun prepareCommitIconColumn() {
    val commitIconColumn = columnModel.getColumn(CommitsTableModel.COMMIT_ICON_COLUMN)
    val renderer = CommitIconRenderer()
    commitIconColumn.cellRenderer = TableCellRenderer { table, _, isSelected, hasFocus, row, column ->
      renderer.update(
        table,
        isSelected,
        hasFocus,
        row,
        column,
        row == table.rowCount - 1,
        getDrawNodeType(row),
        table.editingRow == row,
        getRowHeight(row)
      )
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
    val subjectColumn = columnModel.getColumn(SUBJECT_COLUMN)
    subjectColumn.cellRenderer = SubjectRenderer()
    subjectColumn.cellEditor = CommitMessageCellEditor(project, this, disposable)
  }

  private fun getDrawNodeType(row: Int): CommitIconRenderer.NodeType = when {
    model.getEntry(row).entry.action == GitRebaseEntry.Action.EDIT -> CommitIconRenderer.NodeType.EDIT
    isFixupOrDrop(row) -> CommitIconRenderer.NodeType.NO_NODE
    isFixupRoot(row) -> CommitIconRenderer.NodeType.DOUBLE_NODE
    else -> CommitIconRenderer.NodeType.SIMPLE_NODE
  }

  private fun isFixupOrDrop(row: Int): Boolean {
    val commitRow = model.getEntry(row)
    return commitRow.entry.action == GitRebaseEntry.Action.FIXUP || commitRow.entry.action == GitRebaseEntry.Action.DROP
  }

  private fun isFixupRoot(rowToCheck: Int): Boolean {
    if (isFixupOrDrop(rowToCheck)) {
      return false
    }
    for (row in rowToCheck + 1 until model.rowCount) {
      val rowAction = model.getEntry(row).entry.action
      if (rowAction != GitRebaseEntry.Action.DROP) {
        return rowAction == GitRebaseEntry.Action.FIXUP
      }
    }
    return false
  }

  fun isFirstFixup(rowToCheck: Int): Boolean {
    if (model.getEntry(rowToCheck).entry.action != GitRebaseEntry.Action.FIXUP) {
      return false
    }
    for (row in rowToCheck - 1 downTo 0) {
      val rowAction = model.getEntry(row).entry.action
      if (rowAction != GitRebaseEntry.Action.DROP) {
        return rowAction != GitRebaseEntry.Action.FIXUP
      }
    }
    return true
  }

  fun isLastFixup(rowToCheck: Int): Boolean {
    if (model.getEntry(rowToCheck).entry.action != GitRebaseEntry.Action.FIXUP) {
      return false
    }
    for (row in rowToCheck + 1 until model.rowCount) {
      val rowAction = model.getEntry(row).entry.action
      if (rowAction != GitRebaseEntry.Action.DROP) {
        return rowAction != GitRebaseEntry.Action.FIXUP
      }
    }
    return true
  }

  private class CommitMessageCellEditor(
    project: Project,
    private val table: CommitsTable,
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
      val model = table.model as CommitsTableModel
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
}

private class SubjectRenderer : ColoredTableCellRenderer() {
  companion object {
    private const val GRAPH_WIDTH = 20
    private const val CONNECTION_CENTER_X = GRAPH_WIDTH / 4
    private const val CONNECTION_CENTER_Y = DEFAULT_CELL_HEIGHT / 2
  }

  var graphType: GraphType = GraphType.NoGraph

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
    drawLine(CONNECTION_CENTER_X, CONNECTION_CENTER_Y, xRight, CONNECTION_CENTER_Y)
  }

  private fun Graphics2D.drawDownLine() {
    drawLine(CONNECTION_CENTER_X, CONNECTION_CENTER_Y, CONNECTION_CENTER_X, DEFAULT_CELL_HEIGHT)
  }

  private fun Graphics2D.drawUpLine(withArrow: Boolean) {
    val triangleSide = JBUI.scale(8)
    val triangleBottomY = triangleSide / 2
    val triangleBottomXDiff = triangleSide / 2
    val upLineY = if (withArrow) triangleBottomY else 0
    drawLine(CONNECTION_CENTER_X, CONNECTION_CENTER_Y, CONNECTION_CENTER_X, upLineY)

    if (withArrow) {
      val xPoints = intArrayOf(CONNECTION_CENTER_X, CONNECTION_CENTER_X - triangleBottomXDiff, CONNECTION_CENTER_X + triangleBottomXDiff)
      val yPoints = intArrayOf(0, triangleBottomY, triangleBottomY)
      fillPolygon(xPoints, yPoints, xPoints.size)
    }
  }

  private fun getRowGraphType(table: CommitsTable, row: Int) = if (table.model.getEntry(row).entry.action == GitRebaseEntry.Action.FIXUP) {
    GraphType.FixupGraph(table.isFirstFixup(row), table.isLastFixup(row))
  }
  else {
    GraphType.NoGraph
  }

  override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
    if (value != null) {
      border = null
      isOpaque = false
      val commitsTable = table as CommitsTable
      graphType = getRowGraphType(commitsTable, row)
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
      append(getSubject(entryWithEditedMessage.newMessage), attributes, true)
      SpeedSearchUtil.applySpeedSearchHighlighting(table, this, true, selected)
    }
  }

  private sealed class GraphType {
    object NoGraph : GraphType()
    class FixupGraph(val isFirst: Boolean, val isLast: Boolean) : GraphType()
  }
}


private class CommitIconRenderer : SimpleColoredRenderer() {
  companion object {
    private const val NODE_WIDTH = 8
    private const val NODE_CENTER_X = NODE_WIDTH
    private const val NODE_CENTER_Y = DEFAULT_CELL_HEIGHT / 2
  }

  private var isHead = false
  private var nodeType = NodeType.SIMPLE_NODE
  private var rowHeight = 0

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    (g as Graphics2D).drawCommitIcon()
  }

  fun update(
    table: JTable?,
    isSelected: Boolean,
    hasFocus: Boolean,
    row: Int,
    column: Int,
    isHead: Boolean,
    nodeType: NodeType,
    editing: Boolean,
    rowHeight: Int
  ) {
    clear()
    setPaintFocusBorder(false)
    acquireState(table, isSelected && !editing, hasFocus && !editing, row, column)
    cellState.updateRenderer(this)
    border = null
    this.isHead = isHead
    this.nodeType = nodeType
    this.rowHeight = rowHeight
  }

  private fun Graphics2D.drawCommitIcon() {
    val tableRowHeight = this@CommitIconRenderer.rowHeight
    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    when (nodeType) {
      NodeType.SIMPLE_NODE -> {
        drawNode()
      }
      NodeType.DOUBLE_NODE -> {
        drawDoubleNode()
      }
      NodeType.NO_NODE -> {
      }
      NodeType.EDIT -> {
        drawEditNode()
      }
    }
    if (nodeType != NodeType.EDIT) {
      drawEdge(tableRowHeight, false)
      if (!isHead) {
        drawEdge(tableRowHeight, true)
      }
    }
  }

  private fun Graphics2D.drawDoubleNode() {
    val circleRadius = NODE_WIDTH / 2
    val backgroundCircleRadius = circleRadius + 1
    val leftCircleX0 = NODE_CENTER_X
    val y0 = NODE_CENTER_Y
    val rightCircleX0 = leftCircleX0 + circleRadius

    // right circle
    drawCircle(rightCircleX0, y0)

    // distance between circles
    drawCircle(leftCircleX0, y0, backgroundCircleRadius, this@CommitIconRenderer.background)

    // left circle
    drawCircle(leftCircleX0, y0)
  }

  private fun Graphics2D.drawEditNode() {
    val icon = AllIcons.Actions.Pause
    icon.paintIcon(null, this@drawEditNode, NODE_CENTER_X - icon.iconWidth / 2, NODE_CENTER_Y - icon.iconHeight / 2)
  }

  private fun Graphics2D.drawNode() {
    drawCircle(NODE_CENTER_X, NODE_CENTER_Y)
  }

  private fun Graphics2D.drawCircle(x0: Int, y0: Int, circleRadius: Int = NODE_WIDTH / 2, circleColor: Color = GRAPH_COLOR) {
    val circle = Ellipse2D.Double(
      x0 - circleRadius + 0.5,
      y0 - circleRadius + 0.5,
      2.0 * circleRadius,
      2.0 * circleRadius
    )
    color = circleColor
    fill(circle)
  }

  private fun Graphics2D.drawEdge(tableRowHeight: Int, isDownEdge: Boolean) {
    val y1 = NODE_CENTER_Y
    val y2 = if (isDownEdge) tableRowHeight else 0
    val x = NODE_CENTER_X
    color = GRAPH_COLOR
    stroke = BasicStroke(GRAPH_LINE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL)
    drawLine(x, y1, x, y2)
  }

  enum class NodeType {
    NO_NODE, SIMPLE_NODE, DOUBLE_NODE, EDIT
  }
}

private open class ChangeEntryStateSimpleAction(
  protected val action: GitRebaseEntry.Action,
  title: String,
  description: String,
  icon: Icon?,
  protected val table: CommitsTable
) : AnActionButton(title, description, icon), DumbAware {

  constructor(action: GitRebaseEntry.Action, icon: Icon?, table: CommitsTable) :
    this(action, action.name.capitalize(), action.name.capitalize(), icon, table)

  init {
    val keyStroke = KeyStroke.getKeyStroke(
      KeyEvent.getExtendedKeyCodeForChar(action.mnemonic.toInt()),
      InputEvent.ALT_MASK
    )
    shortcutSet = CustomShortcutSet(KeyboardShortcut(keyStroke, null))
    this.registerCustomShortcutSet(table, null)
  }

  override fun actionPerformed(e: AnActionEvent) {
    table.selectedRows.forEach { row ->
      table.setValueAt(action, row, CommitsTableModel.COMMIT_ICON_COLUMN)
    }
  }

  override fun updateButton(e: AnActionEvent) {
    super.updateButton(e)
    actionIsEnabled(e, true)
    if (table.editingRow != -1 || table.selectedRowCount == 0) {
      actionIsEnabled(e, false)
    }
  }

  protected open fun actionIsEnabled(e: AnActionEvent, isEnabled: Boolean) {
    e.presentation.isEnabled = isEnabled
  }
}

private open class ChangeEntryStateButtonAction(
  action: GitRebaseEntry.Action,
  table: CommitsTable
) : ChangeEntryStateSimpleAction(action, null, table), CustomComponentAction, DumbAware {
  companion object {
    private val BUTTON_HEIGHT = JBUI.scale(28)
  }

  protected val button = object : JButton(action.name.capitalize()) {
    init {
      preferredSize = Dimension(preferredSize.width, BUTTON_HEIGHT)
      border = object : DarculaButtonPainter() {
        override fun getBorderInsets(c: Component?): Insets {
          return JBUI.emptyInsets()
        }
      }
      isFocusable = false
      displayedMnemonicIndex = 0
      addActionListener {
        val toolbar = ComponentUtil.getParentOfType(ActionToolbar::class.java, this)
        val dataContext = toolbar?.toolbarDataContext ?: DataManager.getInstance().getDataContext(this)
        actionPerformed(
          AnActionEvent.createFromAnAction(this@ChangeEntryStateButtonAction, null, GitInteractiveRebaseDialog.PLACE, dataContext)
        )
      }
    }
  }

  override fun actionIsEnabled(e: AnActionEvent, isEnabled: Boolean) {
    super.actionIsEnabled(e, isEnabled)
    button.isEnabled = isEnabled
  }

  override fun createCustomComponent(presentation: Presentation, place: String) = BorderLayoutPanel().addToCenter(button).apply {
    border = JBUI.Borders.emptyLeft(6)
  }
}

private class FixupAction(table: CommitsTable) : ChangeEntryStateButtonAction(GitRebaseEntry.Action.FIXUP, table) {
  override fun actionPerformed(e: AnActionEvent) {
    val selectedRows = table.selectedRows
    if (selectedRows.size == 1) {
      table.setValueAt(action, selectedRows.first(), CommitsTableModel.COMMIT_ICON_COLUMN)
    }
    else {
      selectedRows.drop(1).forEach { row ->
        table.setValueAt(action, row, CommitsTableModel.COMMIT_ICON_COLUMN)
      }
    }
  }
}

private class RewordAction(table: CommitsTable) : ChangeEntryStateButtonAction(GitRebaseEntry.Action.REWORD, table) {
  override fun updateButton(e: AnActionEvent) {
    super.updateButton(e)
    if (table.selectedRowCount != 1) {
      actionIsEnabled(e, false)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    TableUtil.editCellAt(table, table.selectedRows.single(), SUBJECT_COLUMN)
  }
}

private class AnActionButtonSeparator : AnActionButton("Separator"), CustomComponentAction, DumbAware {
  companion object {
    private val SEPARATOR_HEIGHT = JBUI.scale(20)
  }

  override fun actionPerformed(e: AnActionEvent) {
    throw UnsupportedOperationException()
  }

  override fun createCustomComponent(presentation: Presentation, place: String) = JSeparator(SwingConstants.VERTICAL).apply {
    preferredSize = Dimension(preferredSize.width, SEPARATOR_HEIGHT)
  }
}

private class ShowGitRebaseEditorLikeEntriesAction(private val project: Project, private val table: CommitsTable) :
  DumbAwareAction(GitBundle.getString("rebase.interactive.dialog.view.git.commands.text")) {

  private fun getEntries(): List<GitRebaseEntry> = table.model.entries.map { it.entry }

  override fun actionPerformed(e: AnActionEvent) {
    val dialog = GitRebaseEditorLikeEntriesDialog(project, getEntries())
    dialog.show()
  }
}

internal class GitRebaseEntryWithEditedMessage(
  val entry: GitRebaseEntryWithDetails,
  var newMessage: String = entry.commitDetails.fullMessage
)