// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin

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
import git4idea.GitUtil.getRepositoriesFromRoots
import git4idea.GitUtil.getRepositoryManager
import git4idea.GitVcs
import git4idea.repo.GitRepository
import java.awt.event.KeyEvent
import javax.swing.JComponent

class GitSkipHooksCommitHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler = GitSkipHooksCommitHandler(panel)
}

private class GitSkipHooksCommitHandler(private val panel: CheckinProjectPanel) : CheckinHandler() {
  override fun getBeforeCheckinConfigurationPanel() = GitSkipHooksConfigurationPanel(panel)
}

private class GitSkipHooksConfigurationPanel(private val panel: CheckinProjectPanel) :
  RefreshableOnComponent, CheckinChangeListSpecificComponent {

  private val vcs = GitVcs.getInstance(panel.project)
  private val runHooks = NonFocusableCheckBox("Run Git hooks").apply {
    mnemonic = KeyEvent.VK_H
    toolTipText = "<html>If unchecked, Git hook will be skipped with the '--no-verify' parameter</html>"
  }
  private var selectedState = true

  override fun getComponent(): JComponent = JBUI.Panels.simplePanel(runHooks)

  override fun onChangeListSelected(list: LocalChangeList?) {
    if (runHooks.isEnabled) selectedState = runHooks.isSelected
    val affectedGitRoots = panel.roots.intersect(setOf(*ProjectLevelVcsManager.getInstance(panel.project).getRootsUnderVcs(vcs)))
    runHooks.isEnabled = getRepositoriesFromRoots(getRepositoryManager(panel.project), affectedGitRoots).any { it.hasCommitHooks() }
    runHooks.isSelected = if (runHooks.isEnabled) selectedState else false
  }

  override fun refresh() = Unit

  override fun saveState() {
    (vcs.checkinEnvironment as GitCheckinEnvironment).setSkipHooksForNextCommit(shouldSkipHook())
  }

  override fun restoreState() {
    runHooks.isVisible = getRepositoryManager(panel.project).repositories.any { it.hasCommitHooks() }
    runHooks.isSelected = true
  }

  private fun shouldSkipHook() = runHooks.isVisible && !runHooks.isSelected

  private fun GitRepository.hasCommitHooks() = info.hooksInfo.areCommitHooksAvailable
}
