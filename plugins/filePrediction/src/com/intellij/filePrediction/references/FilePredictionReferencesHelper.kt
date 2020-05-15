// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.references

import com.intellij.filePrediction.FileReferencesComputationResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.ApiStatus

internal object FilePredictionReferencesHelper {
  private val EXTERNAL_REFERENCES_EP_NAME = ExtensionPointName<FileExternalReferencesProvider>(
    "com.intellij.filePrediction.referencesProvider")

  fun calculateExternalReferences(project: Project, file: VirtualFile?): FileReferencesComputationResult {
    val start = System.currentTimeMillis()
    val result = ApplicationManager.getApplication().runReadAction(Computable {
      if (file?.isValid == false) {
        return@Computable ExternalReferencesResult.FAILED_COMPUTATION
      }

      val psiFile = file?.let { PsiManager.getInstance(project).findFile(it) }
      if (DumbService.isDumb(project)) {
        return@Computable ExternalReferencesResult.FAILED_COMPUTATION
      }
      psiFile?.let { getReferencesProvider(it) } ?: ExternalReferencesResult.NO_REFERENCES
    })
    return FileReferencesComputationResult(result, start)
  }

  private fun getReferencesProvider(file: PsiFile): ExternalReferencesResult {
    return EXTERNAL_REFERENCES_EP_NAME.extensions.mapNotNull { it.externalReferences(file) }.firstOrNull() ?: ExternalReferencesResult.FAILED_COMPUTATION
  }
}

@ApiStatus.Internal
internal interface FileExternalReferencesProvider {
  fun externalReferences(file: PsiFile): ExternalReferencesResult?
}