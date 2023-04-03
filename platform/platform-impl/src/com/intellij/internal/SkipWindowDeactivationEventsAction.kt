// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.ide.skipWindowDeactivationEvents
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction

internal class SkipWindowDeactivationEventsAction : DumbAwareToggleAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean {
    return skipWindowDeactivationEvents
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    skipWindowDeactivationEvents = state
  }
}
