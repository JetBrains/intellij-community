// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.references.java

import com.intellij.filePrediction.references.ExternalReferencesResult
import com.intellij.filePrediction.references.ExternalReferencesResult.Companion.FAILED_COMPUTATION
import com.intellij.filePrediction.references.ExternalReferencesResult.Companion.succeed
import com.intellij.filePrediction.references.FileExternalReferencesProvider
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
      return succeed(uFile.imports.filter { !it.isOnDemand }.mapNotNull { it.resolve() }.mapNotNull { it.containingFile }.toSet())
    }
    return null
  }
}
