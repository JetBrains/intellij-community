// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package com.intellij.devkit.compose.demo.dialog

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAwareAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.idea.devkit.util.PsiUtil

internal class ComponentShowcaseDialogAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null && PsiUtil.isPluginProject(e.project!!)
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = checkNotNull(event.project) { "Project not available" }

    currentThreadCoroutineScope().launch(Dispatchers.EDT) {
      ComponentShowcaseDialog(project).showAndGet()
    }
  }
}
