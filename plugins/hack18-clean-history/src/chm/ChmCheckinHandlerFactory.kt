// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package chm

import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.ui.NonFocusableCheckBox
import com.intellij.util.PairConsumer
import com.intellij.util.ui.JBUI
import git4idea.GitVcs
import git4idea.checkin.GitCheckinEnvironment
import java.awt.event.KeyEvent
import javax.swing.JComponent

class ChmCheckinHandlerFactory : VcsCheckinHandlerFactory(GitVcs.getKey()) {
  override fun createVcsHandler(panel: CheckinProjectPanel): CheckinHandler {
    return MyCheckinHandler(panel)
  }
}

class MyCheckinHandler(panel: CheckinProjectPanel) : CheckinHandler() {
  private val vcs = GitVcs.getInstance(panel.project)
  private val configurationPanel = MyConfigurationPanel()

  override fun beforeCheckin(executor: CommitExecutor?, additionalDataConsumer: PairConsumer<Any, Any>): ReturnResult {

    if (configurationPanel.isSelected()) {
      val ce = vcs.checkinEnvironment as GitCheckinEnvironment
      ce.myOverridingCommitProcedure = MyCommitProcess()
    }

    return ReturnResult.COMMIT
  }

  override fun getBeforeCheckinConfigurationPanel() = configurationPanel

  class MyConfigurationPanel : RefreshableOnComponent {
    private val cleanupCommit = NonFocusableCheckBox("Cleanup Commit")
    fun  isSelected() = cleanupCommit.isSelected

    override fun getComponent(): JComponent {
      cleanupCommit.mnemonic = KeyEvent.VK_C
      cleanupCommit.isSelected = true
      return JBUI.Panels.simplePanel(cleanupCommit)
    }

    override fun refresh() {}

    override fun saveState() {}

    override fun restoreState() {}

  }
}

