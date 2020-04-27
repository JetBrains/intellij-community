package com.intellij.filePrediction.java

import com.intellij.filePrediction.ExternalReferencesResult
import com.intellij.filePrediction.ExternalReferencesResult.Companion.FAILED_COMPUTATION
import com.intellij.filePrediction.ExternalReferencesResult.Companion.succeed
import com.intellij.filePrediction.FileExternalReferencesProvider
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiFile
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElement

internal class JavaFileReferenceProvider : FileExternalReferencesProvider {

  override fun externalReferences(file: PsiFile): ExternalReferencesResult? {
    val uFile = file.toUElement()
    if (uFile != null && uFile is UFile) {
      if (DumbService.isDumb(file.project)) {
        return FAILED_COMPUTATION
      }
      return succeed(uFile.imports.mapNotNull { it.resolve() }.mapNotNull { it.containingFile }.toSet())
    }
    return null
  }
}
