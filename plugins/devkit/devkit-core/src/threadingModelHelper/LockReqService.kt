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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


private val EP_NAME: ExtensionPointName<LockReqAnalyzer> = create("DevKit.lang.LockReqsAnalyzer")
internal object LockReqAnalyzerProvider : LanguageExtension<LockReqAnalyzer>(EP_NAME.name)

@Service(Service.Level.PROJECT)
class LockReqsService(private val project: Project) {

  private var _currentResults by mutableStateOf<List<AnalysisResult?>>(emptyList())
  val currentResults: List<AnalysisResult?>
    get() = _currentResults

  suspend fun analyzeMethod(methodPtr: SmartPsiElementPointer<PsiMethod>) {
    val analyzer = LockReqAnalyzerProvider.forLanguage(methodPtr.element?.language!!)
    withBackgroundProgress(project, "Analyzing lock requirements", true) {
      val streaming = analyzer as? LockReqAnalyzerStreaming
      if (streaming != null) {
        val method = smartReadAction(project) { methodPtr.element }
        if (method == null) return@withBackgroundProgress
        val consumer = DefaultLockReqConsumer(method) { snapshot ->
          ApplicationManager.getApplication().invokeLater {
            _currentResults = listOf(snapshot)
          }
        }
        smartReadAction(project) {
          streaming.analyzeMethodStreaming(method, consumer)
        }
      } else {
        // Fallback to blocking mode
        val result = smartReadAction(project) {
          val method = methodPtr.element ?: return@smartReadAction null
          analyzer.analyzeMethod(method)
        }
        withContext(Dispatchers.EDT) {
          _currentResults = listOf(result)
        }
      }
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
}