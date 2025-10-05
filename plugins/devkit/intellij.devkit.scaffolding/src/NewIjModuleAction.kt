// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.scaffolding

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAware
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class NewIjModuleAction : AnAction(), DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val dc = e.dataContext
    e.presentation.isEnabledAndVisible = isActionEnabled(dc)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val dc = e.dataContext
    currentThreadCoroutineScope().launch(start = CoroutineStart.UNDISPATCHED) {
      actionPerformed(dc)
    }
  }
}

private suspend fun actionPerformed(dc: DataContext) {
  val project = dc.getData(CommonDataKeys.PROJECT) ?: return
  val root = dc.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
  val suggestedNamePrefix = withContext(Dispatchers.IO) { 
    suggestModuleNamePrefix(root, project) 
  }
  val name = withContext(Dispatchers.EDT) {
    askForModuleName(project, suggestedNamePrefix)
  }
  createIjModule(project, root, name)
}
