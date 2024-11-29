// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.interactive.dialog

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.ui.details.FullCommitDetailsListPanel
import git4idea.history.GitCommitRequirements
import git4idea.history.GitLogUtil
import git4idea.i18n.GitBundle
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.GitRebaseEntryWithDetails
import git4idea.rebase.interactive.GitRebaseTodoModel
import git4idea.rebase.interactive.dialog.view.MoveTableItemRunnable
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JSeparator
import javax.swing.SwingConstants

@ApiStatus.Internal
const val GIT_INTERACTIVE_REBASE_DIALOG_DIMENSION_KEY = "Git.Interactive.Rebase.Dialog"

internal class GitInteractiveRebaseDialog<T : GitRebaseEntry>(
  private val project: Project,
  root: VirtualFile,
  entries: List<T>
) : DialogWrapper(project, true) {
  companion object {
    private const val DETAILS_PROPORTION = "Git.Interactive.Rebase.Details.Proportion"
    internal const val PLACE = "Git.Interactive.Rebase.Dialog"

    private const val DIALOG_HEIGHT = 550
    private const val DIALOG_WIDTH = 1000
  }

  private val commitsTableModel = GitRebaseCommitsTableModel(entries)
  private val resetEntriesLabel = LinkLabel<Any?>(GitBundle.message("rebase.interactive.dialog.reset.link.text"), null).apply {
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
  private val commitsTable = object : GitRebaseCommitsTableView(project, commitsTableModel, disposable) {
    override fun onEditorCreate() {
      isOKActionEnabled = false
    }

    override fun onEditorRemove() {
      isOKActionEnabled = true
    }
  }
  private val modalityState = window?.let { ModalityState.stateForComponent(it) } ?: ModalityState.current()
  private val fullCommitDetailsListPanel = object : FullCommitDetailsListPanel(project, disposable, modalityState) {
    @RequiresBackgroundThread
    @Throws(VcsException::class)
    override fun loadChanges(commits: List<VcsCommitMetadata>): List<Change> {
      val changes = mutableListOf<Change>()
      GitLogUtil.readFullDetailsForHashes(project, root, commits.map { it.id.asString() }, GitCommitRequirements.DEFAULT) { gitCommit ->
        changes.addAll(gitCommit.changes)
      }
      return CommittedChangesTreeBrowser.zipChanges(changes)
    }
  }
  private val iconActions = listOf(
    PickAction(commitsTable),
    EditAction(commitsTable)
  )
  private val rewordAction = RewordAction(commitsTable)
  private val fixupAction = FixupAction(commitsTable)
  private val squashAction = SquashAction(commitsTable)
  private val dropAction = DropAction(commitsTable)

  private val contextMenuOnlyActions = listOf<AnAction>(ShowGitRebaseCommandsDialog(project, commitsTable))
  private var modified = false

  init {
    fun getCommitDetailsFromRow(row: Int): VcsCommitMetadata? {
      val entryWithDetails = commitsTableModel.getEntry(row) as? GitRebaseEntryWithDetails
      return entryWithDetails?.commitDetails
    }
    commitsTable.selectionModel.addListSelectionListener { _ ->
      val commitDetailsList = commitsTable.selectedRows.map { getCommitDetailsFromRow(it) }.filterNotNull()
      fullCommitDetailsListPanel.commitsSelected(commitDetailsList)
    }
    commitsTableModel.addTableModelListener { resetEntriesLabel.isVisible = true }
    commitsTableModel.addTableModelListener { modified = true }
    PopupHandler.installRowSelectionTablePopup(
      commitsTable,
      DefaultActionGroup().apply {
        addAll(iconActions)
        add(rewordAction)
        add(squashAction)
        add(fixupAction)
        add(dropAction)
        addSeparator()
        addAll(contextMenuOnlyActions)
      },
      PLACE
    )

    val diffAction = fullCommitDetailsListPanel.changesBrowser.diffAction
    diffAction.registerCustomShortcutSet(commitsTable, null);

    title = GitBundle.message("rebase.interactive.dialog.title")
    setOKButtonText(GitBundle.message("rebase.interactive.dialog.start.rebase"))
    init()
  }

  override fun getDimensionServiceKey() = GIT_INTERACTIVE_REBASE_DIALOG_DIMENSION_KEY

  override fun createCenterPanel() = BorderLayoutPanel().apply {
    val decorator = ToolbarDecorator.createDecorator(commitsTable)
      .setToolbarPosition(ActionToolbarPosition.TOP)
      .setMoveUpAction(MoveTableItemRunnable(-1, commitsTable))
      .setMoveDownAction(MoveTableItemRunnable(1, commitsTable))
      .setPanelBorder(JBUI.Borders.empty())
      .setScrollPaneBorder(JBUI.Borders.empty())
      .disableAddAction()
      .disableRemoveAction()
      .addExtraActions(*iconActions.toTypedArray())
      .addExtraAction(AnActionButtonSeparator())
      .addExtraAction(rewordAction)
      .addExtraAction(AnActionOptionButton(squashAction, listOf(fixupAction)))
      .addExtraAction(dropAction)

    val tablePanel = decorator.createPanel()
    val resetEntriesLabelPanel = BorderLayoutPanel().addToCenter(resetEntriesLabel).apply {
      border = JBUI.Borders.empty(0, 5, 0, 10)
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

  fun getModel(): GitRebaseTodoModel<T> = commitsTableModel.rebaseTodoModel

  override fun getPreferredFocusedComponent(): JComponent = commitsTable

  override fun doCancelAction() {
    if (modified) {
      val result = Messages.showDialog(
        rootPane,
        GitBundle.message("rebase.interactive.dialog.discard.modifications.message"),
        GitBundle.message("rebase.interactive.dialog.discard.modifications.cancel"),
        arrayOf(
          GitBundle.message("rebase.interactive.dialog.discard.modifications.discard"),
          GitBundle.message("rebase.interactive.dialog.discard.modifications.continue")
        ),
        0,
        Messages.getQuestionIcon()
      )
      if (result != Messages.YES) {
        return
      }
    }
    super.doCancelAction()
  }

  override fun getHelpId(): String {
    return "reference.VersionControl.Git.RebaseCommits"
  }

  private class AnActionButtonSeparator : DumbAwareAction(), CustomComponentAction {
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
}