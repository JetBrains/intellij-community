// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.codeSmells

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel
import com.intellij.openapi.project.Project

internal class FrontendCodeAnalysisPanel(project: Project) : NewErrorTreeViewPanel(
  project,
  null,
  false,
  true,
  null
) {

  fun populate(messages: List<FrontendCodeSmellMessage>) {
    messages.forEach { msg ->
      addMessage(
        type = msg.type,
        text = msg.text,
        groupName = msg.filePath,
        navigatable = msg.navigatable,
        exportTextPrefix = msg.exportPrefix,
        rendererTextPrefix = msg.rendererPrefix,
        data = null
      )
    }
  }

  override fun canHideWarnings(): Boolean = false
}
