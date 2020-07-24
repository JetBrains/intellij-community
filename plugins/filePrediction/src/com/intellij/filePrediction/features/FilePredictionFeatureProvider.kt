// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features

import com.intellij.filePrediction.references.ExternalReferencesResult
import com.intellij.filePrediction.FileFeaturesComputationResult
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

internal object FilePredictionFeaturesHelper {
  private val EP_NAME = ExtensionPointName<FilePredictionFeatureProvider>("com.intellij.filePrediction.featureProvider")

  fun calculateFileFeatures(project: Project,
                            newFile: VirtualFile,
                            refs: ExternalReferencesResult,
                            prevFile: VirtualFile?): FileFeaturesComputationResult {
    val start = System.currentTimeMillis()
    val result = HashMap<String, FilePredictionFeature>()
    for (provider in EP_NAME.extensionList) {
      val prefix = calculateProviderPrefix(provider)
      val features = provider.calculateFileFeatures(project, newFile, prevFile, refs).mapKeys { prefix + it.key }
      result.putAll(features)
    }
    return FileFeaturesComputationResult(result, start)
  }

  fun getFeatureCodes(): Map<String, Int> {
    val codes = hashMapOf<String, Int>()
    for ((index, provider) in EP_NAME.extensionList.withIndex()) {
      val prefix = calculateProviderPrefix(provider)
      for ((featureIndex, feature) in provider.getFeatures().withIndex()) {
        val key = prefix + feature
        val value = 100 * index + featureIndex
        codes[key] = value
      }
    }
    return codes
  }

  private fun calculateProviderPrefix(provider: FilePredictionFeatureProvider) =
    if (provider.getName().isNotEmpty()) provider.getName() + "_" else ""
}

@ApiStatus.Internal
interface FilePredictionFeatureProvider {
  fun getName(): String

  fun getFeatures(): Array<String>

  fun calculateFileFeatures(project: Project,
                            newFile: VirtualFile,
                            prevFile: VirtualFile?,
                            refs: ExternalReferencesResult): Map<String, FilePredictionFeature>
}