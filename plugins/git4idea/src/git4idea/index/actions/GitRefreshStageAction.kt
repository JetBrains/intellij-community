// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.VcsBundle
import git4idea.index.ui.GIT_STAGE_TRACKER
import git4idea.index.vfs.GitIndexFileSystemRefresher

class GitRefreshStageAction : AnAction(VcsBundle.messagePointer("action.name.refresh")) {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.getData(GIT_STAGE_TRACKER) != null && e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getRequiredData(GIT_STAGE_TRACKER).scheduleUpdateAll()
    GitIndexFileSystemRefresher.getInstance(e.project!!).refresh { true }
  }
}