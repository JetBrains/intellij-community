// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.changes.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.vcs.impl.frontend.changes.CHANGE_LISTS_KEY
import com.intellij.vcs.impl.frontend.changes.EDITOR_TAB_DIFF_PREVIEW

class ShowDiffAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val diffPreview = EDITOR_TAB_DIFF_PREVIEW.getData(e.dataContext)
    diffPreview?.performDiffAction()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = !CHANGE_LISTS_KEY.getData(e.dataContext).isNullOrEmpty()
  }
}