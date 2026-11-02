// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper

import com.intellij.devkit.threading.DevkitThreadingBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPsiElementPointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.idea.devkit.threadingModelHelper.AnalysisConfig
import org.jetbrains.idea.devkit.threadingModelHelper.AnalysisResult
import org.jetbrains.idea.devkit.threadingModelHelper.ConstraintType
import org.jetbrains.idea.devkit.threadingModelHelper.DefaultLockReqConsumer
import org.jetbrains.idea.devkit.threadingModelHelper.LOCK_REQUIREMENTS
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqAnalyzerParallelBFS
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqConsumer
import java.nio.file.Path
import java.util.EnumSet

@Service(Service.Level.PROJECT)
internal class LockReqsService(private val project: Project, private val coroutineScope: CoroutineScope) {

  private val _currentResult = MutableStateFlow<AnalysisResult?>(null)

  /**
   * The latest snapshot of the running or the completed analysis, or `null` if no analysis was started.
   */
  val currentResult: StateFlow<AnalysisResult?> = _currentResult.asStateFlow()

  suspend fun analyzeMethod(methodPtr: SmartPsiElementPointer<PsiMethod>, requirements: EnumSet<ConstraintType>) {
    val analyzer = LockReqAnalyzerParallelBFS()
    val config = AnalysisConfig.forProject(project, requirements)
    val title = if (requirements == LOCK_REQUIREMENTS) "locking" else "threading"
    withBackgroundProgress(project, DevkitThreadingBundle.message("progress.title.analyzing.lock.requirements", title), true) {
      val defaultConsumer = DefaultLockReqConsumer(methodPtr) { snapshot ->
        _currentResult.value = snapshot
      }
      val consumer = object : LockReqConsumer by defaultConsumer {
        override fun onStatisticsReport(file: Path) {
          if (Registry.`is`("devkit.threading.open.statistics.report", false)) {
            openStatisticsReport(file)
          }
        }
      }
      analyzer.analyzeMethodStreaming(methodPtr, config, project, consumer)
    }
  }

  /**
   * Opens the statistics report in the editor.
   * The service scope runs this, because the analysis scope can be cancelled already.
   */
  private fun openStatisticsReport(file: Path) {
    coroutineScope.launch {
      val virtualFile = withContext(Dispatchers.IO) {
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)
      } ?: return@launch
      withContext(Dispatchers.EDT) {
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
      }
    }
  }
}
