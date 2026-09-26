// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.core

import com.intellij.ide.EssentialHighlightingMode
import com.intellij.ide.actions.notifyOnEssentialHighlightingMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification

internal class ToggleEssentialHighlightingAction : ToggleAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun isSelected(e: AnActionEvent): Boolean {
    return EssentialHighlightingMode.isEnabled()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    EssentialHighlightingMode.setEnabled(state)
    if (state) {
      notifyOnEssentialHighlightingMode(e.getData(CommonDataKeys.PROJECT))
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
