// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.dialog

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.components.Badge
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowsRange
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.util.ui.UIUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.workingTrees.GitWorktreeAdditionalConfigCopier
import git4idea.workingTrees.GitWorktreeIncludeFileService
import git4idea.workingTrees.GitWorktreeNotifications
import git4idea.workingTrees.GitWorktreeProjectConfigService
import git4idea.workingTrees.WORKTREE_INCLUDE_FILE_NAME
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.IOException
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JSeparator
import javax.swing.SwingConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The "Copy project config" section of the New Worktree dialog: the additional-copier checkboxes, plus the
 * .worktreeinclude create/edit controls.
 *
 * Call [buildPanel] once to add its rows, and [updateWorktreeIncludeButtons] whenever the dialog's selected
 * repository changes.
 */
internal class GitWorktreeConfigCategoriesPanel(
  propertyGraph: PropertyGraph,
  savedState: GitWorkingTreeDialogState?,
  private val project: Project,
  private val getCurrentRepository: () -> GitRepository,
  private val setupScriptPanel: GitWorktreeSetupScriptPanel,
  private val onEditWorktreeIncludeFile: (VirtualFile) -> Unit,
) {
  companion object {
    private val LOG = logger<GitWorktreeConfigCategoriesPanel>()
  }

  private val includeFileService = GitWorktreeIncludeFileService.getInstance(project)
  private val projectConfigService = GitWorktreeProjectConfigService.getInstance(project)

  private val expanded = AtomicBooleanProperty(savedState?.configCategoriesExpanded ?: false)
  private var previousWorktreeIncludeExists: Boolean? = null

  val additionalCopierSelections: Map<GitWorktreeAdditionalConfigCopier, GraphProperty<Boolean>> =
    GitWorktreeAdditionalConfigCopier.getExtensions().associateWith { extension ->
      propertyGraph.property(savedState?.additionalCopiers?.get(extension) ?: true)
    }

  val copyWorktreeIncludeMatches: GraphProperty<Boolean> =
    propertyGraph.property(savedState?.copyWorktreeIncludeMatches ?: true)

  private lateinit var chevron: Cell<JLabel>
  private lateinit var label: Cell<JLabel>
  private lateinit var content: RowsRange
  private lateinit var summaryLabel: JEditorPane
  private lateinit var worktreeIncludeCheckBoxCell: Cell<JBCheckBox>
  private lateinit var worktreeIncludeCreateButton: Cell<JButton>
  private lateinit var worktreeIncludeAddIdeaSettingsButton: Cell<JButton>
  private lateinit var worktreeIncludeEditButton: Cell<JButton>

  fun isExpanded(): Boolean = expanded.get()

  fun buildPanel(panel: Panel) = with(panel) {
    row {
      chevron = icon(UIUtil.getTreeCollapsedIcon())
        .align(AlignY.TOP)
        .gap(RightGap.SMALL)
      panel {
        row {
          label = label(GitBundle.message("working.tree.dialog.group.copy.project.config"))
          icon(Badge.beta).gap(RightGap.SMALL)
          cell(JSeparator(SwingConstants.HORIZONTAL))
            .align(AlignX.FILL)
            .resizableColumn()
        }
        row {
          summaryLabel = comment("", maxLineLength = MAX_LINE_LENGTH_WORD_WRAP).component
        }
      }.align(AlignX.FILL)
    }
    val toggleExpanded = { expanded.set(!expanded.get()) }
    makeClickable(chevron.component, toggleExpanded)
    makeClickable(label.component, toggleExpanded)

    content = indent {
      for ((extension, selected) in additionalCopierSelections) {
        row {
          checkBox(extension.checkboxText).bindSelected(selected)
        }
      }
      row {
        worktreeIncludeCheckBoxCell = checkBox(GitBundle.message("working.tree.dialog.checkbox.worktree.include"))
          .bindSelected(copyWorktreeIncludeMatches)
          .comment(GitBundle.message("working.tree.dialog.worktree.include.comment"), maxLineLength = MAX_LINE_LENGTH_WORD_WRAP)
          .gap(RightGap.SMALL)
        worktreeIncludeCreateButton = button(GitBundle.message("working.tree.dialog.button.worktree.include.create")) {
          val repository = getCurrentRepository()
          val includeIdeaSettings = !isIdeaConfigCommittedToGitBlocking(repository)
          val content = GitWorktreeIncludeFileService.generateWorktreeIncludeContent(includeIdeaSettings)
          if (includeFileService.writeWorktreeIncludeFile(repository, content)) {
            openWorktreeIncludeFileAndClose()
          }
          else {
            notifyWorktreeIncludeFileWriteFailed(repository)
          }
        }.gap(RightGap.SMALL)
        worktreeIncludeAddIdeaSettingsButton =
          button(GitBundle.message("working.tree.dialog.button.worktree.include.add.idea.settings")) {
            val repository = getCurrentRepository()
            if (includeFileService.addIdeaSettingsSection(repository)) {
              openWorktreeIncludeFileAndClose()
            }
            else {
              notifyWorktreeIncludeFileWriteFailed(repository)
            }
          }.gap(RightGap.SMALL)
        worktreeIncludeEditButton = button(GitBundle.message("working.tree.dialog.button.worktree.include.edit")) {
          openWorktreeIncludeFileAndClose()
        }
      }
      setupScriptPanel.buildPanel(this)
    }
    content.visible(expanded.get())
    expanded.afterChange {
      content.visible(it)
      chevron.component.icon = if (it) UIUtil.getTreeExpandedIcon() else UIUtil.getTreeCollapsedIcon()
    }
    updateSummary()
    additionalCopierSelections.values.forEach { it.afterChange { updateSummary() } }
    copyWorktreeIncludeMatches.afterChange { updateSummary() }
    setupScriptPanel.runSetupScript.afterChange { updateSummary() }
    updateWorktreeIncludeButtons()
  }

  private fun updateSummary() {
    val enabledCount = additionalCopierSelections.values.count { it.get() } +
      (if (copyWorktreeIncludeMatches.get()) 1 else 0) +
      (if (setupScriptPanel.runSetupScript.get()) 1 else 0)
    val totalCount = additionalCopierSelections.size + 2
    summaryLabel.text = GitBundle.message("working.tree.dialog.category.summary", enabledCount, totalCount)
  }

  private fun makeClickable(component: JComponent, action: () -> Unit) {
    component.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    component.addMouseListener(object : MouseAdapter() {
      override fun mouseReleased(e: MouseEvent) = action()
    })
  }

  /**
   * Shows the Create button when the repository has no .worktreeinclude yet. Otherwise shows Edit, plus
   * Add idea settings when the file exists, is missing the idea-settings block, and idea settings are not
   * already committed to git.
   */
  fun updateWorktreeIncludeButtons() {
    val repository = getCurrentRepository()
    val worktreeIncludeFile = repository.root.findChild(WORKTREE_INCLUDE_FILE_NAME)
    val worktreeIncludeExists = worktreeIncludeFile != null
    worktreeIncludeCheckBoxCell.enabled(worktreeIncludeExists)
    worktreeIncludeCreateButton.visible(!worktreeIncludeExists)
    worktreeIncludeEditButton.visible(worktreeIncludeExists)
    if (!worktreeIncludeExists || previousWorktreeIncludeExists == false) {
      copyWorktreeIncludeMatches.set(worktreeIncludeExists)
    }
    previousWorktreeIncludeExists = worktreeIncludeExists

    val canAddIdeaSettings = worktreeIncludeFile != null && canOfferIdeaSettingsBlocking(repository, worktreeIncludeFile)
    worktreeIncludeAddIdeaSettingsButton.visible(canAddIdeaSettings)
  }

  /** Hands the repository's existing .worktreeinclude file to [onEditWorktreeIncludeFile]. */
  private fun openWorktreeIncludeFileAndClose() {
    val file = getCurrentRepository().root.findChild(WORKTREE_INCLUDE_FILE_NAME) ?: return
    onEditWorktreeIncludeFile(file)
  }

  /** Blocks the UI thread behind a modal progress to check whether [repository]'s idea config is committed to git. */
  private fun isIdeaConfigCommittedToGitBlocking(repository: GitRepository): Boolean =
    runWithModalProgressBlocking(project, GitBundle.message("working.tree.dialog.worktree.include.checking.idea.config")) {
      projectConfigService.isIdeaConfigCommittedToGit(repository.root)
    }

  /**
   * Blocks the UI thread behind a modal progress to check whether [file] can still gain an idea-settings
   * section: it lacks one yet, and [repository]'s idea config is not already committed to git.
   */
  private fun canOfferIdeaSettingsBlocking(repository: GitRepository, file: VirtualFile): Boolean =
    runWithModalProgressBlocking(project, GitBundle.message("working.tree.dialog.worktree.include.checking.idea.config")) {
      !hasIdeaSettingsSectionSafely(file) && !projectConfigService.isIdeaConfigCommittedToGit(repository.root)
    }

  /** Returns `true` when [file] already has the idea-settings block, or `false` when [file] fails to read. */
  private suspend fun hasIdeaSettingsSectionSafely(file: VirtualFile): Boolean = withContext(Dispatchers.IO) {
    val content = try {
      VfsUtil.loadText(file)
    }
    catch (e: IOException) {
      LOG.warn("Failed to read $file while checking for the idea-settings section", e)
      return@withContext false
    }
    GitWorktreeIncludeFileService.hasIdeaSettingsSection(content)
  }

  private fun notifyWorktreeIncludeFileWriteFailed(repository: GitRepository) {
    GitWorktreeNotifications.notifyWorktreeIncludeFileWriteFailed(project, repository.root)
  }
}
