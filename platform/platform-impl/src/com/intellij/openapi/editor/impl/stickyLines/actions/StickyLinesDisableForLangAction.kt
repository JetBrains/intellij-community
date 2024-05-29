// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines.actions

import com.intellij.idea.ActionsBundle
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable

internal class StickyLinesDisableForLangAction: StickyLinesAbstractAction() {

  override fun update(e: AnActionEvent) {
    val language = stickyLinesLanguage(e)
    if (language != null) {
      e.presentation.text = ActionsBundle.message("action.EditorStickyLinesDisableForLang.for.lang.text", language.displayName)
      e.presentation.isEnabledAndVisible = true
    } else {
      e.presentation.isEnabledAndVisible = false
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val language = stickyLinesLanguage(e)
    if (language != null) {
      EditorSettingsExternalizable.getInstance().setStickyLinesShownFor(language.id, false)
    }
  }

  private fun stickyLinesLanguage(e: AnActionEvent): Language? {
    return if (EditorSettingsExternalizable.getInstance().areStickyLinesShown()) {
      e.getData(CommonDataKeys.PSI_FILE)?.viewProvider?.baseLanguage
    } else {
      null
    }
  }
}
