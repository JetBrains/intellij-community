// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.actions.BasePasteHandler
import com.intellij.openapi.editor.actions.TextComponentEditorAction

internal val IS_SETTINGS_CONTEXT: DataKey<Boolean> = DataKey.create("SettingsContext")

internal class SettingsPasteAction : TextComponentEditorAction(BasePasteHandler()) {
  override fun update(e: AnActionEvent) {
    if (e.dataContext.getData(IS_SETTINGS_CONTEXT) != true) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    super.update(e)
  }
}
