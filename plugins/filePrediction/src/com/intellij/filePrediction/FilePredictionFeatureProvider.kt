// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.filePrediction.ExternalReferencesResult.Companion.FAILED_COMPUTATION
import com.intellij.filePrediction.ExternalReferencesResult.Companion.NO_REFERENCES
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

internal object FilePredictionFeaturesHelper {
  private val EP_NAME = ExtensionPointName<FilePredictionFeatureProvider>("com.intellij.filePrediction.featureProvider")
  private val EXTERNAL_REFERENCES_EP_NAME = ExtensionPointName<FileExternalReferencesProvider>("com.intellij.filePrediction.referencesProvider")

  fun calculateExternalReferences(file: PsiFile?): ExternalReferencesResult {
    return file?.let { getReferencesProvider(it) } ?: NO_REFERENCES
  }

  fun calculateFileFeatures(project: Project,
                            newFile: VirtualFile,
                            prevFile: VirtualFile?): Map<String, FilePredictionFeature> {
    val result = HashMap<String, FilePredictionFeature>()
    val providers = EP_NAME.extensionList
    for (provider in providers) {
      val prefix = if (provider.getName().isNotEmpty()) provider.getName() + "_" else ""
      val features = provider.calculateFileFeatures(project, newFile, prevFile).mapKeys { prefix + it.key }
      result.putAll(features)
    }
    return result
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