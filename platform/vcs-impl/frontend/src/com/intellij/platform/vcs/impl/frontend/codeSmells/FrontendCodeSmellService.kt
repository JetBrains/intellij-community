// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.codeSmells

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import com.intellij.platform.vcs.impl.shared.CodeSmellDto
import com.intellij.platform.vcs.impl.shared.showCodeSmellsPanelInToolWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Service(Level.PROJECT)
internal class FrontendCodeSmellService(private val project: Project, private val cs: CoroutineScope) {

  fun showCodeSmellsInToolWindow(codeSmellDtos: List<CodeSmellDto>) {
    val messages = convertCodeSmellDtosToMessages(codeSmellDtos, project)

    cs.launch(Dispatchers.EDT) {
      val panel = FrontendCodeAnalysisPanel(project)
      panel.populate(messages)

      showCodeSmellsPanelInToolWindow(project, panel)
    }
  }

  companion object {
    fun getInstance(project: Project): FrontendCodeSmellService =
      project.getService(FrontendCodeSmellService::class.java)
  }
}
