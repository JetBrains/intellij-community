// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData

class GitShowDiffFromStashAction : AnActionExtensionProvider {
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
    e.presentation.isEnabled = VcsTreeModelData.all(stashUi.changesBrowser.viewer).userObjectsStream().anyMatch {
      stashUi.changesBrowser.getDiffRequestProducer(it) != null
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val changesBrowser = e.getRequiredData(GitStashUi.GIT_STASH_UI).changesBrowser

    val selection = ListSelection.createAt(VcsTreeModelData.all(changesBrowser.viewer).userObjects(), 0)
    ChangesBrowserBase.showStandaloneDiff(e.project!!, changesBrowser, selection)
  }
}
