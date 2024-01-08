// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive.dialog

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.TableUtil
import git4idea.i18n.GitBundle
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.GitRebaseEntryWithDetails
import git4idea.rebase.interactive.GitRebaseTodoModel
import git4idea.rebase.interactive.convertToEntries
import java.awt.event.InputEvent
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.KeyStroke

private fun getActionShortcutList(actionId: String): List<Shortcut> = KeymapUtil.getActiveKeymapShortcuts(actionId).shortcuts.toList()

private fun findNewRoot(
  rebaseTodoModel: GitRebaseTodoModel<*>,
  elementIndex: Int
): GitRebaseTodoModel.Element<*>? {
  val element = rebaseTodoModel.elements[elementIndex]
  return rebaseTodoModel.elements.take(element.index).findLast {
    it is GitRebaseTodoModel.Element.UniteRoot ||
    (it is GitRebaseTodoModel.Element.Simple && it.type is GitRebaseTodoModel.Type.NonUnite.KeepCommit)
  }
}

private fun getIndicesToUnite(selection: List<Int>, rebaseTodoModel: GitRebaseTodoModel<*>): List<Int>? {
  if (rebaseTodoModel.canUnite(selection)) {
    return selection
  }
  val index = selection.singleOrNull() ?: return null
  val newRoot = findNewRoot(rebaseTodoModel, index) ?: return null
  return (listOf(newRoot.index) + selection).takeIf { rebaseTodoModel.canUnite(it) }
}

internal abstract class ChangeEntryStateSimpleAction(
  protected val action: GitRebaseEntry.KnownAction,
  title: Supplier<String>,
  description: Supplier<String>,
  icon: Icon?,
  private val table: GitRebaseCommitsTableView,
  additionalShortcuts: List<Shortcut> = listOf()
) : AnAction(title, description, icon), DumbAware {
  constructor(
    action: GitRebaseEntry.KnownAction,
    icon: Icon?,
    table: GitRebaseCommitsTableView,
    additionalShortcuts: List<Shortcut> = listOf()
  ) : this(action, action.visibleName, action.visibleName, icon, table, additionalShortcuts)

  init {
    val keyStroke = KeyStroke.getKeyStroke(
      action.mnemonic,
      InputEvent.ALT_MASK
    )
    val shortcuts = additionalShortcuts + KeyboardShortcut(keyStroke, null)
    shortcutSet = CustomShortcutSet(*shortcuts.toTypedArray())
    this.registerCustomShortcutSet(table, null)
  }

  final override fun actionPerformed(e: AnActionEvent) {
    updateModel(::performEntryAction)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun update(e: AnActionEvent) {
    val hasSelection = table.editingRow == -1 && table.selectedRowCount != 0
    e.presentation.isEnabled = hasSelection && isEntryActionEnabled(table.selectedRows.toList(), table.model.rebaseTodoModel)
  }

  protected abstract fun performEntryAction(selection: List<Int>, rebaseTodoModel: GitRebaseTodoModel<out GitRebaseEntryWithDetails>)

  protected abstract fun isEntryActionEnabled(selection: List<Int>, rebaseTodoModel: GitRebaseTodoModel<*>): Boolean

  private fun updateModel(f: (List<Int>, GitRebaseTodoModel<out GitRebaseEntryWithDetails>) -> Unit) {
    val model = table.model
    val selectedRows = table.selectedRows.toList()
    val selectedEntries = selectedRows.map { model.getEntry(it) }
    table.model.updateModel { rebaseTodoModel ->
      f(selectedRows, rebaseTodoModel)
    }
    restoreSelection(selectedEntries)
  }

  private fun restoreSelection(selectedEntries: List<GitRebaseEntry>) {
    val selectedEntriesSet = selectedEntries.toSet()
    val newSelection = mutableListOf<Int>()
    table.model.rebaseTodoModel.elements.forEachIndexed { index, element ->
      if (element.entry in selectedEntriesSet) {
        newSelection.add(index)
      }
    }
    TableUtil.selectRows(table, newSelection.toIntArray())
  }

  protected fun reword(row: Int) {
    TableUtil.selectRows(table, intArrayOf(row))
    TableUtil.editCellAt(table, row, GitRebaseCommitsTableModel.SUBJECT_COLUMN)
  }
}

internal abstract class ChangeEntryStateButtonAction(
  action: GitRebaseEntry.KnownAction,
  table: GitRebaseCommitsTableView,
  additionalShortcuts: List<Shortcut> = listOf()
) : ChangeEntryStateSimpleAction(action, null, table, additionalShortcuts), CustomComponentAction, DumbAware {
  protected val button = object : JButton(action.visibleName.get()) {
    init {
      adjustForToolbar()
      addActionListener {
        val dataContext = ActionToolbar.getDataContextFor(this)
        actionPerformed(
          AnActionEvent.createFromAnAction(this@ChangeEntryStateButtonAction, null, GitInteractiveRebaseDialog.PLACE, dataContext)
        )
      }
      mnemonic = action.mnemonic
    }
  }

  private val buttonPanel = button.withLeftToolbarBorder()

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    button.isEnabled = presentation.isEnabled
  }

  override fun createCustomComponent(presentation: Presentation, place: String) = buttonPanel
}

internal class FixupAction(table: GitRebaseCommitsTableView) : ChangeEntryStateSimpleAction(GitRebaseEntry.Action.FIXUP, null, table) {
  override fun isEntryActionEnabled(selection: List<Int>, rebaseTodoModel: GitRebaseTodoModel<*>) =
    getIndicesToUnite(selection, rebaseTodoModel) != null

  override fun performEntryAction(selection: List<Int>, rebaseTodoModel: GitRebaseTodoModel<out GitRebaseEntryWithDetails>) {
    rebaseTodoModel.unite(getIndicesToUnite(selection, rebaseTodoModel)!!)
  }
}

// squash = reword + fixup
internal class SquashAction(private val table: GitRebaseCommitsTableView) :
  ChangeEntryStateSimpleAction(GitRebaseEntry.Action.SQUASH, null, table) {

  override fun isEntryActionEnabled(selection: List<Int>, rebaseTodoModel: GitRebaseTodoModel<*>) =
    getIndicesToUnite(selection, rebaseTodoModel) != null

  override fun performEntryAction(selection: List<Int>, rebaseTodoModel: GitRebaseTodoModel<out GitRebaseEntryWithDetails>) {
    val indicesToUnite = getIndicesToUnite(selection, rebaseTodoModel)!!
    val currentRoot = indicesToUnite.firstOrNull()?.let { rebaseTodoModel.elements[it] }?.let { element ->
      when (element) {
        is GitRebaseTodoModel.Element.UniteRoot -> element
        is GitRebaseTodoModel.Element.UniteChild -> element.root
        is GitRebaseTodoModel.Element.Simple -> null
      }
    }
    val currentChildrenCount = currentRoot?.children?.size

    val root = rebaseTodoModel.unite(indicesToUnite)
    if (currentRoot != null) {
      // added commits to already squashed
      val newChildren = root.children.drop(currentChildrenCount!!)
      val model = table.model
      rebaseTodoModel.reword(
        root.index,
        (listOf(root) + newChildren).joinToString("\n".repeat(3)) { model.getCommitMessage(it.index) }
      )
    }
    else {
      rebaseTodoModel.reword(root.index, root.getUnitedCommitMessage { it.commitDetails.fullMessage })
    }
    reword(root.index)
  }
}

internal class RewordAction(table: GitRebaseCommitsTableView) :
  ChangeEntryStateButtonAction(GitRebaseEntry.Action.REWORD, table, getActionShortcutList("Git.Reword.Commit")) {

  override fun isEntryActionEnabled(selection: List<Int>, rebaseTodoModel: GitRebaseTodoModel<*>) =
    selection.size == 1 && rebaseTodoModel.canReword(selection.single())

  override fun performEntryAction(selection: List<Int>, rebaseTodoModel: GitRebaseTodoModel<out GitRebaseEntryWithDetails>) {
    reword(selection.single())
  }
}

internal class PickAction(table: GitRebaseCommitsTableView) :
  ChangeEntryStateSimpleAction(GitRebaseEntry.Action.PICK, AllIcons.Actions.Rollback, table) {

  override fun isEntryActionEnabled(selection: List<Int>, rebaseTodoModel: GitRebaseTodoModel<*>) = rebaseTodoModel.canPick(selection)

  override fun performEntryAction(selection: List<Int>, rebaseTodoModel: GitRebaseTodoModel<out GitRebaseEntryWithDetails>) {
    rebaseTodoModel.pick(selection)
  }
}

internal class EditAction(table: GitRebaseCommitsTableView) :
  ChangeEntryStateSimpleAction(
    GitRebaseEntry.Action.EDIT,
    GitBundle.messagePointer("rebase.interactive.dialog.stop.to.edit.text"),
    GitBundle.messagePointer("rebase.interactive.dialog.stop.to.edit.text"),
    AllIcons.Actions.Pause,
    table
  ) {
  override fun isEntryActionEnabled(selection: List<Int>, rebaseTodoModel: GitRebaseTodoModel<*>) = rebaseTodoModel.canEdit(selection)

  override fun performEntryAction(selection: List<Int>, rebaseTodoModel: GitRebaseTodoModel<out GitRebaseEntryWithDetails>) {
    rebaseTodoModel.edit(selection)
  }
}

internal class DropAction(table: GitRebaseCommitsTableView) :
  ChangeEntryStateButtonAction(GitRebaseEntry.Action.DROP, table, getActionShortcutList(IdeActions.ACTION_DELETE)) {

  override fun isEntryActionEnabled(selection: List<Int>, rebaseTodoModel: GitRebaseTodoModel<*>) = rebaseTodoModel.canDrop(selection)

  override fun performEntryAction(selection: List<Int>, rebaseTodoModel: GitRebaseTodoModel<out GitRebaseEntryWithDetails>) {
    rebaseTodoModel.drop(selection)
  }
}

internal class ShowGitRebaseCommandsDialog(private val project: Project, private val table: GitRebaseCommitsTableView) :
  DumbAwareAction(GitBundle.messagePointer("rebase.interactive.dialog.view.git.commands.text")) {

  private fun getEntries(): List<GitRebaseEntry> = table.model.rebaseTodoModel.convertToEntries()

  override fun actionPerformed(e: AnActionEvent) {
    val dialog = GitRebaseCommandsDialog(project, getEntries())
    dialog.show()
  }
}