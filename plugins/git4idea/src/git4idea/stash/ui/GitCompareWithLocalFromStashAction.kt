// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider
import com.intellij.openapi.vcs.changes.actions.ShowDiffWithLocalAction
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase

class GitCompareWithLocalFromStashAction: AnActionExtensionProvider {
  override fun isActive(e: AnActionEvent): Boolean {
    return e.getData(GitStashUi.GIT_STASH_UI) != null &&
           e.getData(ChangesBrowserBase.DATA_KEY) == null
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val stashUi = e.getData(GitStashUi.GIT_STASH_UI)
    if (project == null || stashUi == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isVisible = true
    e.presentation.isEnabled = stashUi.changesBrowser.changes.any { change ->
      ShowDiffWithLocalAction.getChangeWithLocal(change, false) != null
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val changes = e.getRequiredData(GitStashUi.GIT_STASH_UI).changesBrowser.changes
    val changesWithLocal = changes.mapNotNull { change ->
      ShowDiffWithLocalAction.getChangeWithLocal(change, false)
    }
    ShowDiffAction.showDiffForChange(project, changesWithLocal)
  }
}