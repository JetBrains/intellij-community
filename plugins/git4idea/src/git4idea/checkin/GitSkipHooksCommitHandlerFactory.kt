// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin

import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.ui.NonFocusableCheckBox
import com.intellij.util.ui.JBUI
import com.intellij.vcs.commit.commitProperty
import git4idea.GitUtil.getRepositoryManager
import git4idea.GitVcs
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.awt.event.KeyEvent
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

  private val vcs = GitVcs.getInstance(panel.project)
  private val runHooks = NonFocusableCheckBox(GitBundle.message("checkbox.run.git.hooks")).apply {
    mnemonic = KeyEvent.VK_H
    toolTipText = GitBundle.message("tooltip.run.git.hooks")
  }
  private var selectedState = true

  override fun getComponent(): JComponent = JBUI.Panels.simplePanel(runHooks)

  override fun onChangeListSelected(list: LocalChangeList) {
    if (runHooks.isEnabled) selectedState = runHooks.isSelected
    val affectedGitRoots = panel.roots.intersect(setOf(*ProjectLevelVcsManager.getInstance(panel.project).getRootsUnderVcs(vcs)))
    val repositoryManager = GitRepositoryManager.getInstance(panel.project)
    runHooks.isEnabled = affectedGitRoots.any { repositoryManager.getRepositoryForRootQuick(it)?.hasCommitHooks() == true }
    runHooks.isSelected = if (runHooks.isEnabled) selectedState else false
  }

  override fun refresh() = Unit

  override fun saveState() {
    commitContext.isSkipHooks = shouldSkipHook()
  }

  override fun restoreState() {
    runHooks.isVisible = getRepositoryManager(panel.project).repositories.any { it.hasCommitHooks() }
    runHooks.isSelected = true
  }

  private fun shouldSkipHook() = runHooks.isVisible && !runHooks.isSelected

  private fun GitRepository.hasCommitHooks() = info.hooksInfo.areCommitHooksAvailable
}
