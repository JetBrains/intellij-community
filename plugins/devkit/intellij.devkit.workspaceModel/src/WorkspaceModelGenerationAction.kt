// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager

internal class WorkspaceModelGenerationAction: AnAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val module = event.getData(LangDataKeys.MODULE) ?: return

    WorkspaceModelGenerator.getInstance(project).generate(module)
  }
  

  override fun update(event: AnActionEvent) {
    if (!isIntellijProjectOrRegistryKeyIsSet(event.project)) {
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

internal class WorkspaceModelGenerateAllModulesAction: AnAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return

    val modules = ModuleManager.getInstance(project).modules
    val modulesSize = modules.size
    log.info("Updating $modulesSize modules")
    WorkspaceModelGenerator.getInstance(project).generate(modules)
  }

  override fun update(event: AnActionEvent) {
    if (!isIntellijProjectOrRegistryKeyIsSet(event.project)) {
      event.presentation.isEnabledAndVisible = false
      return
    }
  }
  
  companion object {
    val log: Logger = logger<WorkspaceModelGenerateAllModulesAction>()
  }
}
