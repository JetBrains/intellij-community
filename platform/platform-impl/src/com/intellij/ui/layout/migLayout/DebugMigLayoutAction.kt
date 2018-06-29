// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout.migLayout

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import net.miginfocom.layout.LayoutUtil

private class DebugMigLayoutAction : ToggleAction(), DumbAware {
  private var debugEnabled = false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    debugEnabled = state
    LayoutUtil.setGlobalDebugMillis(if (debugEnabled) 300 else 0)
  }

  override fun isSelected(e: AnActionEvent) = debugEnabled
}