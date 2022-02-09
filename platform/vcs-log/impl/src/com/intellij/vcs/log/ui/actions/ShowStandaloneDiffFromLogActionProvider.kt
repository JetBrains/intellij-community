// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys

class ShowStandaloneDiffFromLogActionProvider : AnActionExtensionProvider {
  override fun isActive(e: AnActionEvent): Boolean {
    return e.getData(VcsLogInternalDataKeys.MAIN_UI) != null && e.getData(ChangesBrowserBase.DATA_KEY) == null
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val logUi = e.getData(VcsLogInternalDataKeys.MAIN_UI)
    if (project == null || logUi == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isVisible = true
    e.presentation.isEnabled = logUi.changesBrowser.canShowDiff()
  }

  override fun actionPerformed(e: AnActionEvent) {
    ChangesBrowserBase.showStandaloneDiff(e.project!!, e.getRequiredData(VcsLogInternalDataKeys.MAIN_UI).changesBrowser)
  }
}