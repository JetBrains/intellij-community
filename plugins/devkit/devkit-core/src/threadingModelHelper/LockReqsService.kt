// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiMethod

@Service(Service.Level.PROJECT)
class LockReqsService(private val project: Project) {
  private var currentResults: List<String> = emptyList()

  fun updateResults(method: PsiMethod) {
    val analyzer = LockReqsAnalyzer()
    val paths = analyzer.analyzeMethod(method).map { it.pathString }
    currentResults = paths
    ToolWindowManager.getInstance(project).getToolWindow("LockReqsToolWindow")?.show()
  }

  fun getCurrentResults(): List<String> = currentResults
}
