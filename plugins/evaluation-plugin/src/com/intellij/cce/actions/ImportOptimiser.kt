package com.intellij.cce.actions

import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object ImportOptimiser {
  suspend fun optimiseImports(project: Project, file: VirtualFile) {
    val psiFile = PsiManager.getInstance(project).findFile(file)!!
    optimizeImportsAsync(project, psiFile)
  }

  private suspend fun optimizeImportsAsync(project: Project, file: PsiFile) {
    suspendCancellableCoroutine { continuation ->
      val processor = OptimizeImportsProcessor(project, arrayOf(file)) {
        continuation.resume(Unit)
      }
      processor.run()
    }
  }
}