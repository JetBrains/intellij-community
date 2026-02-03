package com.intellij.cce.actions

import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object ImportOptimiser {
  private val LOGGER = logger<ImportOptimiser>()

  suspend fun optimiseImports(project: Project, file: VirtualFile) {
    edtWriteAction { PsiDocumentManager.getInstance(project).commitAllDocuments() }
    val psiFile = readAction { PsiManager.getInstance(project).findFile(file)!! }
    optimizeImportsAsync(project, psiFile)
    edtWriteAction { PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(psiFile.fileDocument) }
    LOGGER.info("Optimised imports for file ${file.path}\n result: ${psiFile.text}")
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