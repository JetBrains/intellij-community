// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features

import com.intellij.filePrediction.references.ExternalReferencesResult
import com.intellij.filePrediction.FileFeaturesComputationResult
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus

internal object FilePredictionFeaturesHelper {
  private val EP_NAME = ExtensionPointName<FilePredictionFeatureProvider>("com.intellij.filePrediction.featureProvider")

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
}

@ApiStatus.Internal
interface FilePredictionFeatureProvider {
  fun getName(): String

  fun calculateFileFeatures(project: Project, newFile: VirtualFile, prevFile: VirtualFile?): Map<String, FilePredictionFeature>
}