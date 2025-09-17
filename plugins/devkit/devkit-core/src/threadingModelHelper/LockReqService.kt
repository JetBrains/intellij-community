// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.openapi.progress.ProgressManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.SmartPsiElementPointer


private val EP_NAME: ExtensionPointName<LockReqAnalyzer> = create("DevKit.lang.LockReqsAnalyzer")
internal object LockReqAnalyzerProvider : LanguageExtension<LockReqAnalyzer>(EP_NAME.name)

@Service(Service.Level.PROJECT)
class LockReqsService(private val project: Project) {

  private  var _currentResults: List<AnalysisResult?> = emptyList()
  val currentResults: List<AnalysisResult?>
    get() = _currentResults

  suspend fun analyzeMethod(methodPtr: SmartPsiElementPointer<PsiMethod>) {
    val analyzer = LockReqAnalyzerProvider.forLanguage(methodPtr.element?.language!!)
    withBackgroundProgress(project, "", true) {
      val result = smartReadAction(project) {
        val method = methodPtr.element ?: return@smartReadAction null
        analyzer.analyzeMethod(method)
      }
      _currentResults = listOf(result)
    }
  }

  suspend fun analyzeClass(psiPtr: SmartPsiElementPointer<PsiClass>) {
    val analyzer = JavaLockReqAnalyzerBFS()
    withBackgroundProgress(project, "", true) {
      val results = smartReadAction(project) {
        val psiClass = psiPtr.element ?: return@smartReadAction emptyList()
        psiClass.methods.map { method ->
          ProgressManager.checkCanceled()
          analyzer.analyzeMethod(method)
        }
      }
      _currentResults = results
    }
  }

  suspend fun analyzeFile(filePtr: SmartPsiElementPointer<PsiJavaFile>) {
    val analyzer = JavaLockReqAnalyzerBFS()
    withBackgroundProgress(project, "", true) {
      val results = smartReadAction(project) {
        val psiFile = filePtr.element ?: return@smartReadAction emptyList()
        psiFile.classes.flatMap { psiClass ->
          psiClass.methods.map { method ->
            ProgressManager.checkCanceled()
            analyzer.analyzeMethod(method)
          }
        }
      }
      _currentResults = results
    }
  }

  //private fun displayResults() {
  //  _currentResults.forEach { result ->
  //    println("Method: ${result?.method?.containingClass?.qualifiedName}.${result?.method?.name}")
  //    result?.paths?.forEach { path ->
  //      println("  Path: ${path.methodChain.joinToString(" -> ") { it.method.name }}")
  //      println("  Requirement: ${path.lockRequirement}")
  //    }
  //  }
  //}

  //var onResultsUpdated: ((AnalysisResult?) -> Unit)? = null
  //
  //fun updateResults(method: PsiMethod) {
  //  val analyzer = LockReqsAnalyzerDFS()
  //  _currentResult = analyzer.analyzeMethod(method)
  //  onResultsUpdated?.invoke(_currentResult)
  //
  //  ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.show()
  //}

  companion object {
    const val TOOL_WINDOW_ID: String = "Lock Requirements"
  }
}