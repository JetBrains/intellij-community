// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

object FilePredictionFeaturesHelper {
  private val EP_NAME = ExtensionPointName.create<FilePredictionFeatureProvider>("com.intellij.filePrediction.featureProvider")
  private val EXTERNAL_REFERENCES_EP_NAME = ExtensionPointName.create<FileExternalReferencesProvider>("com.intellij.filePrediction.referencesProvider")

  fun calculateExternalReferences(file: PsiFile?): Set<PsiFile> {
    return file?.let { getReferencesProvider(it) } ?: emptySet()
  }

  fun calculateFileFeatures(project: Project,
                            newFile: VirtualFile,
                            prevFile: VirtualFile?): Map<String, FilePredictionFeature> {
    val result = HashMap<String, FilePredictionFeature>()
    val providers = getFeatureProviders()
    providers.forEach { provider ->
      val prefix = if (provider.getName().isNotEmpty()) provider.getName() + "_" else ""
      val features = provider.calculateFileFeatures(project, newFile, prevFile).mapKeys { prefix + it.key }
      result.putAll(features)
    }
    return result
  }

  private fun getReferencesProvider(file: PsiFile): Set<PsiFile> {
    return EXTERNAL_REFERENCES_EP_NAME.extensions.flatMap { it.externalReferences(file) }.toSet()
  }

  private fun getFeatureProviders(): List<FilePredictionFeatureProvider> {
    return EP_NAME.extensionList
  }
}

@ApiStatus.Internal
interface FileExternalReferencesProvider {
  fun externalReferences(file: PsiFile): Set<PsiFile>
}

@ApiStatus.Internal
interface FilePredictionFeatureProvider {

  fun getName(): String

  fun calculateFileFeatures(project: Project, newFile: VirtualFile, prevFile: VirtualFile?): Map<String, FilePredictionFeature>
}