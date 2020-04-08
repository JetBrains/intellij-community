/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.checkin

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.UnresolvedMergeCheckProvider
import com.intellij.util.containers.MultiMap
import com.intellij.util.ui.JBUI
import git4idea.GitVcs
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import javax.swing.JComponent
import javax.swing.JLabel

class GitUnresolvedMergeCheckProvider : UnresolvedMergeCheckProvider() {
  override fun checkUnresolvedConflicts(panel: CheckinProjectPanel,
                                        commitContext: CommitContext,
                                        executor: CommitExecutor?): CheckinHandler.ReturnResult? {
    if (executor != null) return null
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
                                 "Can't commit changes due to unresolved conflicts.",
                                 "Unresolved Conflicts",
                                 Messages.getWarningIcon())
      return CheckinHandler.ReturnResult.CANCEL
    }

    // Duplicates dialog from GitCheckinEnvironment.mergeCommit, so is disabled for `git commit --only` mode
    if (Registry.`is`("git.force.commit.using.staging.area")) {
      val changesExcludedFromMerge = repositories.filter { it.state == Repository.State.MERGING }
        .flatMap { groupedChanges[it].subtract(selectedChanges) }
      if (changesExcludedFromMerge.isNotEmpty()) {
        val dialog = MyExcludedChangesDialog(project, changesExcludedFromMerge)
        if (!dialog.showAndGet()) return CheckinHandler.ReturnResult.CANCEL
      }
    }

    return CheckinHandler.ReturnResult.COMMIT
  }

  private class MyExcludedChangesDialog(project: Project, changes: List<Change>) : DialogWrapper(project) {
    val browser = SimpleChangesBrowser(project, changes)

    init {
      title = "Changes Excluded from Merge Commit"
      setOKButtonText("Commit Anyway")

      init()
    }

    override fun createNorthPanel(): JComponent? {
      val label = JLabel("Are you sure you want to exclude these changed files from merge commit?")
      label.border = JBUI.Borders.empty(5, 1)
      return label
    }

    override fun createCenterPanel(): JComponent? = browser
    override fun getPreferredFocusedComponent(): JComponent? = browser.preferredFocusedComponent
  }
}
