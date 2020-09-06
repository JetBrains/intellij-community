// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features

import com.intellij.filePrediction.features.FilePredictionFeature.Companion.binary
import com.intellij.filePrediction.features.FilePredictionFeature.Companion.fileType
import com.intellij.filePrediction.references.ExternalReferencesResult
import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ThreeState

class FilePredictionGeneralFeatures : FilePredictionFeatureProvider {
  companion object {
    private val FEATURES = arrayListOf(
      "file_type",
      "in_ref",
      "prev_file_type"
    )
  }

  override fun getName(): String = "core"

  override fun getFeatures(): List<String> = FEATURES

  override fun calculateFileFeatures(project: Project,
                                     newFile: VirtualFile,
                                     prevFile: VirtualFile?,
                                     cache: FilePredictionFeaturesCache): Map<String, FilePredictionFeature> {
    val result = HashMap<String, FilePredictionFeature>()
    val isInRef = cache.refs.contains(newFile)
    if (isInRef != ThreeState.UNSURE) {
      result["in_ref"] = binary(isInRef == ThreeState.YES)
    }

    result["file_type"] = fileType(FileTypeUsagesCollector.getSafeFileTypeName(newFile.fileType))
    if (prevFile != null) {
      result["prev_file_type"] = fileType(FileTypeUsagesCollector.getSafeFileTypeName(prevFile.fileType))
    }
    return result
  }
}
