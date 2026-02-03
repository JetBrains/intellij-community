// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features

import com.intellij.filePrediction.FileFeaturesComputationResult
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

internal object FilePredictionFeaturesHelper {
  internal val EP_NAME = ExtensionPointName<FilePredictionFeatureProvider>("com.intellij.filePrediction.featureProvider")

  fun calculateFileFeatures(project: Project,
                            newFile: VirtualFile,
                            cache: FilePredictionFeaturesCache,
                            prevFile: VirtualFile?): FileFeaturesComputationResult {
    val start = System.currentTimeMillis()
    val result = HashMap<String, FilePredictionFeature>()
    for (provider in EP_NAME.extensionList) {
      val prefix = calculateProviderPrefix(provider)
      val features = provider.calculateFileFeatures(project, newFile, prevFile, cache).mapKeys { prefix + it.key }
      result.putAll(features)
    }
    return FileFeaturesComputationResult(result, start)
  }

  fun getFeaturesByProviders(): List<List<String>> {
    val orderedFeatures = arrayListOf<List<String>>()
    for (provider in getOrderedFeatureProviders()) {
      val prefix = calculateProviderPrefix(provider)
      orderedFeatures.add(provider.getFeatures().map { prefix + it }.toList())
    }
    return orderedFeatures
  }

  private fun getOrderedFeatureProviders(): List<FilePredictionFeatureProvider> {
    return EP_NAME.extensionList.sortedBy { it.getName() }
  }

  private fun calculateProviderPrefix(provider: FilePredictionFeatureProvider) =
    if (provider.getName().isNotEmpty()) provider.getName() + "_" else ""
}

@ApiStatus.Internal
interface FilePredictionFeatureProvider {
  fun getName(): String

  fun getFeatures(): List<String>

  fun calculateFileFeatures(project: Project,
                            newFile: VirtualFile,
                            prevFile: VirtualFile?,
                            cache: FilePredictionFeaturesCache): Map<String, FilePredictionFeature>
}