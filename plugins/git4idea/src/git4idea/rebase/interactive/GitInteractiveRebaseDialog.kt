// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
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
import com.intellij.vcs.log.graph.EdgePrintElement
import com.intellij.vcs.log.graph.NodePrintElement
import com.intellij.vcs.log.graph.PrintElement
import com.intellij.vcs.log.paint.ColorGenerator
import com.intellij.vcs.log.paint.PaintParameters
import com.intellij.vcs.log.paint.SimpleGraphCellPainter
import com.intellij.vcs.log.ui.details.FullCommitDetailsListPanel
import git4idea.history.GitCommitRequirements
import git4idea.history.GitLogUtil
import git4idea.i18n.GitBundle
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.GitRebaseEntryWithDetails
import git4idea.rebase.interactive.CommitsTableModel.Companion.SUBJECT_COLUMN
import org.jetbrains.annotations.CalledInBackground
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
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

    private const val DIALOG_HEIGHT = 450
    private const val DIALOG_WIDTH = 800
  }

  private val commitsTableModel = CommitsTableModel(entries.map {
    GitRebaseEntryWithEditedMessage(
      GitRebaseEntryWithDetails(GitRebaseEntry(it.action, it.commit, it.subject), it.commitDetails)
    )
  })
  private val resetEntriesLabel = LinkLabel<Any?>("Reset", null).apply {
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
  private val commitsTable = object : CommitsTable(project, commitsTableModel) {
    override fun onEditorCreate() {
      isOKActionEnabled = false
    }

    override fun onEditorRemove() {
      isOKActionEnabled = true
    }
  }
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
  private val actions = listOf<AnAction>(
    ChangeEntryStateAction(GitRebaseEntry.Action.PICK, AllIcons.Actions.Checked, commitsTable),
    ChangeEntryStateAction(GitRebaseEntry.Action.EDIT, "Stop to Edit", "Stop to Edit", AllIcons.Actions.Pause, commitsTable),
    ChangeEntryStateAction(GitRebaseEntry.Action.DROP, AllIcons.Actions.GC, commitsTable),
    FixupAction(commitsTable),
    RewordAction(commitsTable)
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
      DefaultActionGroup(actions),
      "Git.Interactive.Rebase.Dialog",
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
    actions.forEach {
      decorator.addExtraAction(AnActionButton.fromAction(it))
    }

    decorator.addExtraAction(AnActionButton.fromAction(ShowGitRebaseEditorLikeEntriesAction(project, commitsTable)))

    val tablePanel = decorator.createPanel()
    val resetEntriesLabelPanel = BorderLayoutPanel().addToCenter(resetEntriesLabel).apply {
      border = JBUI.Borders.emptyRight(10)
    }
    decorator.actionsPanel.apply {
      border = JBUI.Borders.empty(2, 0)
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
        rows[rowIndex].action = aValue
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

private open class CommitsTable(val project: Project, val model: CommitsTableModel) : JBTable(model) {
  companion object {
    private const val DEFAULT_CELL_HEIGHT = PaintParameters.ROW_HEIGHT
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
        shouldDrawNode(row),
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
    subjectColumn.cellRenderer = object : ColoredTableCellRenderer() {
      override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
        if (value != null) {
          border = null
          isOpaque = false
          val entryWithEditedMessage = this@CommitsTable.model.getEntry(row)
          var attributes: SimpleTextAttributes? = null
          when (entryWithEditedMessage.entry.action) {
            GitRebaseEntry.Action.EDIT -> {
              icon = AllIcons.Actions.Pause
            }
            GitRebaseEntry.Action.DROP -> {
              attributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, null)
            }
            GitRebaseEntry.Action.REWORD -> {
              attributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.BLUE)
            }
            GitRebaseEntry.Action.FIXUP -> {
              icon = AllIcons.Vcs.Merge
            }
            else -> {
            }
          }

          if (attributes != null) {
            append(getSubject(entryWithEditedMessage.newMessage), attributes)
          }
          else {
            append(getSubject(entryWithEditedMessage.newMessage))
          }

          SpeedSearchUtil.applySpeedSearchHighlighting(table, this, true, selected)
        }
      }
    }

    subjectColumn.cellEditor = CommitMessageCellEditor(project, this)
  }

  private fun shouldDrawNode(row: Int): Boolean {
    val entryWithEditedMessage = model.getEntry(row)
    return entryWithEditedMessage.entry.action != GitRebaseEntry.Action.FIXUP &&
           entryWithEditedMessage.entry.action != GitRebaseEntry.Action.DROP
  }

  private class CommitMessageCellEditor(project: Project, private val table: CommitsTable) : AbstractCellEditor(), TableCellEditor {
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

private class CommitIconRenderer : SimpleColoredRenderer() {
  companion object {
    private val UP_EDGE = object : EdgePrintElement {
      override fun getPositionInOtherRow(): Int = 0
      override fun getType(): EdgePrintElement.Type = EdgePrintElement.Type.UP
      override fun getLineStyle(): EdgePrintElement.LineStyle = EdgePrintElement.LineStyle.SOLID
      override fun hasArrow(): Boolean = false
      override fun getRowIndex(): Int = 0
      override fun getPositionInCurrentRow(): Int = 0
      override fun getColorId(): Int = 0
      override fun isSelected(): Boolean = false
    }
    private val NODE = object : NodePrintElement {
      override fun getRowIndex(): Int = 0
      override fun getPositionInCurrentRow(): Int = 0
      override fun getColorId(): Int = 0
      override fun isSelected(): Boolean = false
    }
  }

  private val nodeColor = DefaultColorGenerator().getColor(1)
  private val painter = object : SimpleGraphCellPainter(ColorGenerator { nodeColor }) {
    fun drawDownLine(g2: Graphics2D) {
      val tableRowHeight = this@CommitIconRenderer.rowHeight
      val nodeWidth = PaintParameters.getNodeWidth(rowHeight)
      val y2: Int = tableRowHeight
      val y1: Int = rowHeight / 2
      val x: Int = nodeWidth / 2
      g2.color = nodeColor
      g2.stroke = BasicStroke(PaintParameters.getLineThickness(rowHeight), BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL)
      g2.drawLine(x, y1, x, y2)
    }
  }
  private var isHead = false
  private var withNode = true
  private var rowHeight = 0

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    drawCommitIcon(g as Graphics2D)
  }

  fun update(
    table: JTable?,
    isSelected: Boolean,
    hasFocus: Boolean,
    row: Int,
    column: Int,
    isHead: Boolean,
    withNode: Boolean,
    editing: Boolean,
    rowHeight: Int
  ) {
    clear()
    setPaintFocusBorder(false)
    acquireState(table, isSelected && !editing, hasFocus && !editing, row, column)
    cellState.updateRenderer(this)
    border = null
    this.isHead = isHead
    this.withNode = withNode
    this.rowHeight = rowHeight
  }

  private fun drawCommitIcon(g2: Graphics2D) {
    val elements = mutableListOf<PrintElement>(UP_EDGE)
    if (withNode) {
      elements.add(NODE)
    }
    painter.draw(g2, elements)
    if (!isHead) {
      painter.drawDownLine(g2)
    }
  }
}

private open class ChangeEntryStateAction(
  protected val action: GitRebaseEntry.Action,
  title: String,
  description: String,
  icon: Icon,
  protected val table: CommitsTable
) : DumbAwareAction(title, description, icon) {

  constructor(action: GitRebaseEntry.Action, icon: Icon, table: CommitsTable) :
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

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (table.editingRow != -1 || table.selectedRowCount == 0) {
      e.presentation.isEnabled = false
    }
  }
}

private class FixupAction(table: CommitsTable) : ChangeEntryStateAction(GitRebaseEntry.Action.FIXUP, AllIcons.Vcs.Merge, table) {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.text = when (table.selectedRowCount) {
      0 -> "Fixup"
      1 -> "Fixup with Previous"
      else -> "Fixup Selected"
    }
    e.presentation.description = e.presentation.text
  }

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

private class RewordAction(table: CommitsTable) :
  ChangeEntryStateAction(GitRebaseEntry.Action.REWORD, TITLE, TITLE, AllIcons.Actions.Edit, table) {

  companion object {
    private const val TITLE = "Edit Message"
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (table.selectedRowCount != 1) {
      e.presentation.isEnabled = false
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    TableUtil.editCellAt(table, table.selectedRows.single(), SUBJECT_COLUMN)
  }
}

private class ShowGitRebaseEditorLikeEntriesAction(private val project: Project, private val table: CommitsTable) :
  DumbAwareAction("Show Entries", "Show Entries", AllIcons.General.Information) {

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