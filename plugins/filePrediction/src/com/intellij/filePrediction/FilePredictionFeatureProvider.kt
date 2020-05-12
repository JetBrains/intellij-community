// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.filePrediction.ExternalReferencesResult.Companion.FAILED_COMPUTATION
import com.intellij.filePrediction.ExternalReferencesResult.Companion.NO_REFERENCES
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus

internal object FilePredictionFeaturesHelper {
  private val EP_NAME = ExtensionPointName<FilePredictionFeatureProvider>("com.intellij.filePrediction.featureProvider")
  private val EXTERNAL_REFERENCES_EP_NAME = ExtensionPointName<FileExternalReferencesProvider>("com.intellij.filePrediction.referencesProvider")

  fun calculateExternalReferences(project: Project, file: VirtualFile?): FileReferencesComputationResult {
    val start = System.currentTimeMillis()
    val result = ApplicationManager.getApplication().runReadAction(Computable {
      if (file?.isValid == false) {
        return@Computable FAILED_COMPUTATION
      }

      val psiFile = file?.let { PsiManager.getInstance(project).findFile(it) }
      if (DumbService.isDumb(project)) {
        return@Computable FAILED_COMPUTATION
      }
      psiFile?.let { getReferencesProvider(it) } ?: NO_REFERENCES
    })
    return FileReferencesComputationResult(result, start)
  }

  fun calculateFileFeatures(project: Project,
                            newFile: VirtualFile,
                            refs: ExternalReferencesResult,
                            prevFile: VirtualFile?): FileFeaturesComputationResult {
    val start = System.currentTimeMillis()
    val result = HashMap<String, FilePredictionFeature>()
    val isInRef = refs.contains(newFile)
    if (isInRef != ThreeState.UNSURE) {
      result["in_ref"] = FilePredictionFeature.binary(isInRef == ThreeState.YES)
    }

    val providers = EP_NAME.extensionList
    for (provider in providers) {
      val prefix = if (provider.getName().isNotEmpty()) provider.getName() + "_" else ""
      val features = provider.calculateFileFeatures(project, newFile, prevFile).mapKeys { prefix + it.key }
      result.putAll(features)
    }
    return FileFeaturesComputationResult(result, start)
  }

  private fun getReferencesProvider(file: PsiFile): ExternalReferencesResult {
    return EXTERNAL_REFERENCES_EP_NAME.extensions.mapNotNull { it.externalReferences(file) }.firstOrNull() ?: FAILED_COMPUTATION
  }
}

@ApiStatus.Internal
internal interface FileExternalReferencesProvider {
  fun externalReferences(file: PsiFile): ExternalReferencesResult?
}

@ApiStatus.Internal
interface FilePredictionFeatureProvider {
  fun getName(): String

  fun calculateFileFeatures(project: Project, newFile: VirtualFile, prevFile: VirtualFile?): Map<String, FilePredictionFeature>
}