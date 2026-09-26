// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.module.ModuleUtilCore

internal class ToggleInstallerAction : ToggleAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return IdeStarterRunSettings.getInstance(project).useInstaller
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    IdeStarterRunSettings.getInstance(project).useInstaller = state
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = isIdeStarterContext(e)
  }
}

internal class ToggleSplitModeAction : ToggleAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return IdeStarterRunSettings.getInstance(project).useSplitMode
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    IdeStarterRunSettings.getInstance(project).useSplitMode = state
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = isIdeStarterContext(e)
  }
}

private fun isIdeStarterContext(e: AnActionEvent): Boolean {
  val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return false
  val module = ModuleUtilCore.findModuleForPsiElement(psiFile) ?: return false
  return isIdeStarterModule(module)
}
