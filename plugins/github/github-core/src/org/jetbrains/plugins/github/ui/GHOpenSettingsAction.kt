// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.currentOrDefaultProject

class GHOpenSettingsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = currentOrDefaultProject(e.project)
    ShowSettingsUtil.getInstance().showSettingsDialog(project, GithubSettingsConfigurable::class.java)
  }
  
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
