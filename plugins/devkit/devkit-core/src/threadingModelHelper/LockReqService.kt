// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.SmartPsiElementPointer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.Cancellation.checkCancelled
import com.intellij.psi.SmartPointerManager


@Service(Service.Level.PROJECT)
class LockReqsService(private val project: Project) {

  private var _currentResults by mutableStateOf<List<AnalysisResult?>>(emptyList())
  val currentResults: List<AnalysisResult?>
    get() = _currentResults

  suspend fun analyzeMethod(methodPtr: SmartPsiElementPointer<PsiMethod>) {
    val analyzer = LockReqAnalyzerParallelBFS()
    val config = AnalysisConfig.forProject(project, LOCK_REQUIREMENTS)
    withBackgroundProgress(project, "Analyzing lock requirements", true) {
      val method = smartReadAction(project) { methodPtr.element }
      if (method == null) return@withBackgroundProgress
      val consumer = DefaultLockReqConsumer(methodPtr) { snapshot ->
        ApplicationManager.getApplication().invokeLater {
          _currentResults = listOf(snapshot)
        }
      }
      analyzer.analyzeMethodStreaming(methodPtr, config, project, consumer)
    }
  }

  suspend fun analyzeClass(psiPtr: SmartPsiElementPointer<PsiClass>) {
    val config = AnalysisConfig.forProject(project, LOCK_REQUIREMENTS)
    val analyzer = LockReqAnalyzerParallelBFS()

    withBackgroundProgress(project, "", true) {
      val psiClass = psiPtr.element
      val results = psiClass?.methods?.map { method ->
        checkCancelled()
        analyzer.analyzeMethod(SmartPointerManager.createPointer(method), config)
      } ?: emptyList()
      _currentResults = results
    }
  }

  suspend fun analyzeFile(filePtr: SmartPsiElementPointer<PsiJavaFile>) {
    val config = AnalysisConfig.forProject(project, LOCK_REQUIREMENTS)
    val analyzer = LockReqAnalyzerParallelBFS()

    withBackgroundProgress(project, "", true) {
      val psiFile = filePtr.element
      val results = psiFile?.classes?.flatMap { psiClass ->
        psiClass.methods.map { method ->
          checkCancelled()
          analyzer.analyzeMethod(SmartPointerManager.createPointer(method), config)
        }
      } ?: emptyList()
      _currentResults = results
    }
  }
}