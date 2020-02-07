// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive.dialog

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
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
import org.jetbrains.annotations.CalledInBackground
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JSeparator
import javax.swing.SwingConstants

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

  private val commitsTableModel = GitRebaseCommitsTableModel(entries.map {
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
  private val iconActions = listOf(
    ChangeEntryStateSimpleAction(GitRebaseEntry.Action.PICK, AllIcons.Actions.Rollback, commitsTable),
    ChangeEntryStateSimpleAction(
      GitRebaseEntry.Action.EDIT,
      GitBundle.getString("rebase.interactive.dialog.stop.to.edit.text"),
      GitBundle.getString("rebase.interactive.dialog.stop.to.edit.text"),
      AllIcons.Actions.Pause,
      commitsTable
    )
  )
  private val buttonActions = listOf(
    RewordAction(commitsTable),
    FixupAction(commitsTable),
    ChangeEntryStateButtonAction(GitRebaseEntry.Action.DROP, commitsTable)
  )
  private val contextMenuOnlyActions = listOf<AnAction>(ShowGitRebaseCommandsDialog(project, commitsTable))
  private var modified = false

  init {
    commitsTable.selectionModel.addListSelectionListener { e ->
      if (!e.valueIsAdjusting) {
        fullCommitDetailsListPanel.commitsSelected(commitsTable.selectedRows.map { commitsTableModel.getEntry(it).entry.commitDetails })
      }
    }
    commitsTableModel.addTableModelListener { resetEntriesLabel.isVisible = true }
    commitsTableModel.addTableModelListener { modified = true }
    PopupHandler.installRowSelectionTablePopup(
      commitsTable,
      DefaultActionGroup().apply {
        addAll(iconActions)
        addAll(buttonActions)
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
      .addExtraActions(*iconActions.toTypedArray())
      .addExtraAction(AnActionButtonSeparator())
    buttonActions.forEach {
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

  override fun doCancelAction() {
    if (modified) {
      val result = Messages.showDialog(
        rootPane,
        GitBundle.getString("rebase.interactive.dialog.discard.modifications.message"),
        GitBundle.getString("rebase.interactive.dialog.discard.modifications.cancel"),
        arrayOf(
          GitBundle.getString("rebase.interactive.dialog.discard.modifications.discard"),
          GitBundle.getString("rebase.interactive.dialog.discard.modifications.continue")
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
}