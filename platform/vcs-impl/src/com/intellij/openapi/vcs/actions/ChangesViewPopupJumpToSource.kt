// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions

import com.intellij.diff.actions.impl.OpenInEditorAction
import com.intellij.ide.actions.EditSourceAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil.copyFrom
import com.intellij.openapi.fileEditor.OpenFileDescriptor

internal class ChangesViewPopupJumpToSourceAction : EditSourceAction() {
  init {
    copyFrom(this, "EditSource")
  }

  /**
   * [OpenFileDescriptor.NAVIGATE_IN_EDITOR] set in [com.intellij.diff.tools.holders.TextEditorHolder] preventing
   * navigation outside the diff editor tab.
   */
  override fun actionPerformed(e: AnActionEvent) {
    val navigatables = getNavigatables(e.dataContext) ?: return
    OpenInEditorAction.openEditor(navigatables, null)
  }
}