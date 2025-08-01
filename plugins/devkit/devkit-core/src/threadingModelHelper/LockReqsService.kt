// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiMethod

@Service(Service.Level.PROJECT)
class LockReqsService(private val project: Project) {

  companion object {
    const val TOOL_WINDOW_ID: String = "Locking Requirements"
  }

  private var _currentResults: List<String> = emptyList()
  val currentResults: List<String>
    get() = _currentResults

  var onResultsUpdated: (() -> Unit)? = null

  fun updateResults(method: PsiMethod) {
    val analyzer = LockReqsAnalyzer()
    val paths = analyzer.analyzeMethod(method)
    _currentResults = paths.map { it.pathString }
    onResultsUpdated?.invoke()

    ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.show()
  }
}