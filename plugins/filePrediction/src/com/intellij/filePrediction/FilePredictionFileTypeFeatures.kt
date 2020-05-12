// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.filePrediction.FilePredictionFeature.Companion.categorical
import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class FilePredictionFileTypeFeatures: FilePredictionFeatureProvider {
  override fun getName(): String = ""

  override fun calculateFileFeatures(project: Project, newFile: VirtualFile, prevFile: VirtualFile?): Map<String, FilePredictionFeature> {
    val result = HashMap<String, FilePredictionFeature>()
    result["file_type"] = categorical(FileTypeUsagesCollector.getSafeFileTypeName(newFile.fileType))

    if (prevFile != null) {
      result["prev_file_type"] = categorical(FileTypeUsagesCollector.getSafeFileTypeName(prevFile.fileType))
    }
    return result
  }
}

