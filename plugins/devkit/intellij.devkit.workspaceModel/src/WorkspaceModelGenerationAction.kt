// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.openapi.actionSystem.*
import org.jetbrains.idea.devkit.util.PsiUtil

class WorkspaceModelGenerationAction: AnAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val module = event.getData(LangDataKeys.MODULE) ?: return

    WorkspaceModelGenerator.generate(project, module)
  }

  override fun update(event: AnActionEvent) {
    if (!PsiUtil.isIdeaProject(event.project)) {
      event.presentation.isEnabledAndVisible = false
      return
    }

    if (event.place == ActionPlaces.ACTION_SEARCH) {
      event.presentation.isEnabledAndVisible = true
    } else {
      event.presentation.isEnabledAndVisible = event.getData(LangDataKeys.MODULE_CONTEXT) != null
    }
  }
}