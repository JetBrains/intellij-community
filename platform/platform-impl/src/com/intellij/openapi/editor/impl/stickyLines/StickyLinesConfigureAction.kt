// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.openapi.actionSystem.AnActionEvent

internal class StickyLinesConfigureAction : StickyLinesAbstractAction() {

  override fun actionPerformed(e: AnActionEvent) {
    showStickyLinesSettingsDialog(e.project)
  }
}
