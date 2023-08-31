// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log.command

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.vcs.log.impl.VcsLogContentUtil
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties
import com.intellij.vcs.log.impl.VcsLogTabsManager
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import git4idea.GitVcs
import java.util.*

@Suppress("HardCodedStringLiteral") // internal action
class ShowGitLogCommandAction : DumbAwareAction("Show Git Log for Command") {

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible = ProjectLevelVcsManager.getInstance(project).singleVCS?.keyInstanceMethod == GitVcs.getKey()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    VcsProjectLog.runWhenLogIsReady(project) { vcsLogManager ->
      val uiFactory = GitLogCommandUiFactory("command-log-" + UUID.randomUUID().toString(),
                                             VcsLogFilterObject.collection(), project.service<VcsLogProjectTabsProperties>(),
                                             vcsLogManager.colorManager)
      val ui = VcsLogContentUtil.openLogTab(project, vcsLogManager, VcsLogTabsManager.TAB_GROUP_ID,
                                            { it.filterUi.filters[GitLogCommandFilter.KEY]?.command ?: "" }, uiFactory, true)
      ui.filterUi.addFilterListener { VcsLogContentUtil.updateLogUiName(project, ui) }
      IdeFocusManager.getInstance(project).requestFocus(ui.filterUi.textFilterComponent.focusedComponent, true)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}