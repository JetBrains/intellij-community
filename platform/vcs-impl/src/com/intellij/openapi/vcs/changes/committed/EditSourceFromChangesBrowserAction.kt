// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.EditSourceAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsDataKeys.SELECTED_CHANGES
import com.intellij.openapi.vcs.changes.ChangesUtil.getNavigatableArray
import com.intellij.openapi.vcs.changes.ChangesUtil.iterateFiles
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowserUseCase.IN_AIR
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.pom.Navigatable

internal class EditSourceFromChangesBrowserAction : EditSourceAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.apply {
      icon = AllIcons.Actions.EditSource
      text = VcsBundle.message("edit.source.action.text")

      val isModalContext = e.getData(PlatformCoreDataKeys.IS_MODAL_CONTEXT) == true
      val changesBrowser = e.getData(ChangesBrowserBase.DATA_KEY)
      isVisible = isVisible && changesBrowser != null
      isEnabled = isEnabled && changesBrowser != null &&
                  !isModalContext &&
                  e.getData(CommittedChangesBrowserUseCase.DATA_KEY) != IN_AIR
    }
  }

  override fun getNavigatables(dataContext: DataContext): Array<Navigatable>? {
    val project = PROJECT.getData(dataContext) ?: return null
    val changes = SELECTED_CHANGES.getData(dataContext) ?: return null

    return getNavigatableArray(project, iterateFiles(changes.asList()))
  }
}