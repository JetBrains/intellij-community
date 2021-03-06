// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.vcs.VcsShowToolWindowTabAction
import git4idea.index.GitStageContentProvider
import git4idea.index.isStagingAreaAvailable

class GitShowStagingAreaAction: VcsShowToolWindowTabAction() {
  override val tabName: String get() = GitStageContentProvider.STAGING_AREA_TAB_NAME

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.project?.let {
      if (!isStagingAreaAvailable(it)) e.presentation.isEnabledAndVisible = false
    }
  }
}