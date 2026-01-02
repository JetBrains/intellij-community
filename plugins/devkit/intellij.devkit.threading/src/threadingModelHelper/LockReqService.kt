// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.devkit.threading.DevkitThreadingBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.idea.devkit.threadingModelHelper.*
import java.util.*

@Service(Service.Level.PROJECT)
internal class LockReqsService(private val project: Project) {

  private var _currentResults by mutableStateOf<List<AnalysisResult?>>(emptyList())
  val currentResults: List<AnalysisResult?>
    get() = _currentResults

  suspend fun analyzeMethod(methodPtr: SmartPsiElementPointer<PsiMethod>, requirements: EnumSet<ConstraintType>) {
    val analyzer = LockReqAnalyzerParallelBFS()
    val config = AnalysisConfig.forProject(project, requirements)
    val title = if (requirements == LOCK_REQUIREMENTS) "locking" else "threading"
    withBackgroundProgress(project, DevkitThreadingBundle.message("progress.title.analyzing.lock.requirements", title), true) {
      val consumer = DefaultLockReqConsumer(methodPtr) { snapshot ->
        ApplicationManager.getApplication().invokeLater {
          _currentResults = listOf(snapshot)
        }
      }
      analyzer.analyzeMethodStreaming(methodPtr, config, project, consumer)
    }
  }
}