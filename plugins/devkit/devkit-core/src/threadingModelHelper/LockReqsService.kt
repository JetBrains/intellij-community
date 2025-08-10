// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiMethod

@Service(Service.Level.PROJECT)
class LockReqsService(private val project: Project) {

  companion object {
    const val TOOL_WINDOW_ID: String = "Lock Requirements"
  }

  private var _currentResult: LockReqsAnalyzer.Companion.AnalysisResult? = null
  val currentResult: LockReqsAnalyzer.Companion.AnalysisResult?
    get() = _currentResult

  var onResultsUpdated: ((LockReqsAnalyzer.Companion.AnalysisResult?) -> Unit)? = null

  fun updateResults(method: PsiMethod) {
    val analyzer = LockReqsAnalyzer()
    val paths = analyzer.analyzeMethod(method)
    _currentResult = LockReqsAnalyzer.Companion.AnalysisResult(method, paths)
    onResultsUpdated?.invoke(_currentResult)

    ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.show()
  }
}