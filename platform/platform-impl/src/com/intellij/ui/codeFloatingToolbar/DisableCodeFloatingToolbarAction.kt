// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.options.advanced.AdvancedSettings
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DisableCodeFloatingToolbarAction: AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    AdvancedSettings.setBoolean("floating.codeToolbar.hide", true)
    val editor = e.dataContext.getData(CommonDataKeys.EDITOR)
    CodeFloatingToolbar.getToolbar(editor)?.scheduleHide()
  }
}