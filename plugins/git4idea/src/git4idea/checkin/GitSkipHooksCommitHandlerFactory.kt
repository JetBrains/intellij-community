// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin

import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.util.ui.JBUI
import com.intellij.vcs.commit.CommitSessionCollector
import com.intellij.vcs.commit.CommitSessionCounterUsagesCollector.CommitOption
import com.intellij.vcs.commit.commitProperty
import git4idea.GitVcs
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import javax.swing.JCheckBox
import javax.swing.JComponent

private val IS_SKIP_HOOKS_KEY = Key.create<Boolean>("Git.Commit.IsSkipHooks")
internal var CommitContext.isSkipHooks: Boolean by commitProperty(IS_SKIP_HOOKS_KEY)

class GitSkipHooksCommitHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    if (!panel.vcsIsAffected(GitVcs.NAME)) return CheckinHandler.DUMMY

    return GitSkipHooksCommitHandler(panel, commitContext)
  }
}

private class GitSkipHooksCommitHandler(
  private val panel: CheckinProjectPanel,
  private val commitContext: CommitContext
) : CheckinHandler() {

  override fun getBeforeCheckinConfigurationPanel() = GitSkipHooksConfigurationPanel(panel, commitContext)
}

private class GitSkipHooksConfigurationPanel(
  private val panel: CheckinProjectPanel,
  private val commitContext: CommitContext
) : RefreshableOnComponent,
    CheckinChangeListSpecificComponent {

  private val repositoryManager get() = GitRepositoryManager.getInstance(panel.project)
  private val runHooks = JCheckBox(GitBundle.message("checkbox.run.git.hooks")).apply {
    isSelected = true
    toolTipText = GitBundle.message("tooltip.run.git.hooks")
    addActionListener {
      CommitSessionCollector.getInstance(panel.project).logCommitOptionToggled(CommitOption.RUN_HOOKS, isSelected)
    }
  }

  override fun getComponent(): JComponent = JBUI.Panels.simplePanel(runHooks)

  private fun refreshAvailability() {
    runHooks.isVisible = repositoryManager.repositories.any { it.hasCommitHooks() }
  }

  override fun onChangeListSelected(list: LocalChangeList) {
    refreshAvailability()
  }

  override fun saveState() {
    commitContext.isSkipHooks = runHooks.isVisible && !runHooks.isSelected
  }

  override fun restoreState() {
    refreshAvailability()
  }

  private fun GitRepository.hasCommitHooks() = info.hooksInfo.areCommitHooksAvailable
}
