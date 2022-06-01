// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys

class WorkspaceModelGenerationAction: AnAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val module = event.getData(LangDataKeys.MODULE) ?: return

    WorkspaceModelGenerator.generate(project, module)
  }

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = event.getData(LangDataKeys.MODULE_CONTEXT) != null
  }
}