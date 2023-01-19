// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CommitInfo
import com.intellij.openapi.vcs.checkin.UnresolvedMergeCheckProvider
import com.intellij.util.containers.MultiMap
import com.intellij.util.ui.JBUI
import git4idea.GitVcs
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import javax.swing.JComponent
import javax.swing.JLabel

class GitUnresolvedMergeCheckProvider : UnresolvedMergeCheckProvider() {
  override fun checkUnresolvedConflicts(panel: CheckinProjectPanel,
                                        commitContext: CommitContext,
                                        commitInfo: CommitInfo): CheckinHandler.ReturnResult? {
    if (!commitInfo.isVcsCommit) return null
    if (!panel.vcsIsAffected(GitVcs.NAME)) return null

    val project = panel.project
    val repositoryManager = GitRepositoryManager.getInstance(project)
    val changeListManager = ChangeListManager.getInstance(project)

    val selectedChanges = panel.selectedChanges.toSet()
    val repositories = selectedChanges.mapNotNull { repositoryManager.getRepositoryForFileQuick(ChangesUtil.getFilePath(it)) }.toSet()

    val groupedChanges = MultiMap<GitRepository, Change>()
    for (change in changeListManager.allChanges) {
      val repo = repositoryManager.getRepositoryForFileQuick(ChangesUtil.getFilePath(change))
      if (repositories.contains(repo)) groupedChanges.putValue(repo, change)
    }

    val hasConflicts = groupedChanges.values().any { it.fileStatus === FileStatus.MERGED_WITH_CONFLICTS }
    if (hasConflicts) {
      Messages.showMessageDialog(panel.component,
                                 GitBundle.message("message.unresolved.conflicts.prevent.commit"),
                                 GitBundle.message("title.unresolved.conflicts.pre.commit.check"),
                                 Messages.getWarningIcon())
      return CheckinHandler.ReturnResult.CANCEL
    }

    val changesExcludedFromMerge = repositories.filter { it.state == Repository.State.MERGING }
      .flatMap { groupedChanges[it].subtract(selectedChanges) }
    if (changesExcludedFromMerge.isNotEmpty()) {
      val dialog = MyExcludedChangesDialog(project, changesExcludedFromMerge)
      if (!dialog.showAndGet()) return CheckinHandler.ReturnResult.CANCEL
    }

    return CheckinHandler.ReturnResult.COMMIT
  }

  private class MyExcludedChangesDialog(project: Project, changes: List<Change>) : DialogWrapper(project) {
    val browser = SimpleChangesBrowser(project, changes)

    init {
      title = GitBundle.message("title.changes.excluded.from.commit")
      setOKButtonText(GitBundle.message("button.changes.excluded.from.commit.commit.anyway"))

      init()
    }

    override fun createNorthPanel(): JComponent {
      val label = JLabel(GitBundle.message("label.changes.excluded.from.commit.are.you.sure.want.to.continue"))
      label.border = JBUI.Borders.empty(5, 1)
      return label
    }

    override fun createCenterPanel(): JComponent = browser
    override fun getPreferredFocusedComponent(): JComponent = browser.preferredFocusedComponent
  }
}
