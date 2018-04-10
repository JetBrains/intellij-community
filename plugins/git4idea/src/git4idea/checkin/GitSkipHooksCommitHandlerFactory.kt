/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.ui.NonFocusableCheckBox
import com.intellij.util.PairConsumer
import com.intellij.util.ui.JBUI
import git4idea.GitUtil.getRepositoriesFromRoots
import git4idea.GitUtil.getRepositoryManager
import git4idea.GitVcs
import git4idea.repo.GitRepository
import java.awt.event.KeyEvent
import javax.swing.JComponent

class GitSkipHooksCommitHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return MyCheckinHandler(panel)
  }

  private class MyCheckinHandler(panel: CheckinProjectPanel) : CheckinHandler() {
    private val vcs = GitVcs.getInstance(panel.project)
    private val configurationPanel = MyConfigurationPanel(panel, vcs)

    override fun beforeCheckin(executor: CommitExecutor?, additionalDataConsumer: PairConsumer<Any, Any>): ReturnResult {
      val checkinEnvironment = vcs.checkinEnvironment as GitCheckinEnvironment
      checkinEnvironment.setSkipHooksForNextCommit(configurationPanel.shouldSkipHook())
      return ReturnResult.COMMIT
    }

    override fun getBeforeCheckinConfigurationPanel() = configurationPanel

    private class MyConfigurationPanel(private val panel: CheckinProjectPanel,
                                       private val vcs: GitVcs) : RefreshableOnComponent, CheckinChangeListSpecificComponent {
      private val runHooks = NonFocusableCheckBox("Run Git hooks")
      private var selectedState = true

      override fun getComponent(): JComponent {
        runHooks.mnemonic = KeyEvent.VK_H
        runHooks.toolTipText = "<html>If unchecked, Git hook will be skipped with the '--no-verify' parameter</html>"
        runHooks.isVisible = getRepositoryManager(panel.project).repositories.any { it.hasPreCommitHook() }
        runHooks.isSelected = true
        return JBUI.Panels.simplePanel(runHooks)
      }

      override fun onChangeListSelected(list: LocalChangeList?) {
        if (runHooks.isEnabled) selectedState = runHooks.isSelected
        val affectedGitRoots = panel.roots.intersect(setOf(*ProjectLevelVcsManager.getInstance(panel.project).getRootsUnderVcs(vcs)))
        runHooks.isEnabled = getRepositoriesFromRoots(getRepositoryManager(panel.project), affectedGitRoots).any { it.hasPreCommitHook() }
        runHooks.isSelected = if (runHooks.isEnabled) selectedState else false
      }

      override fun refresh() {}

      override fun saveState() {}

      override fun restoreState() {}

      fun shouldSkipHook() = runHooks.isVisible && !runHooks.isSelected

      private fun GitRepository.hasPreCommitHook() = info.hooksInfo.isPreCommitHookAvailable
    }
  }
}
