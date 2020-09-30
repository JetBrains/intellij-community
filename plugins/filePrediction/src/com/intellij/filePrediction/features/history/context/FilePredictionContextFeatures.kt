// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history.context

import com.intellij.filePrediction.features.FilePredictionFeature
import com.intellij.filePrediction.features.FilePredictionFeatureProvider
import com.intellij.filePrediction.features.FilePredictionFeaturesCache
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class FilePredictionContextFeatures: FilePredictionFeatureProvider {
  companion object {
    private val FEATURES = arrayListOf("opened", "prev_opened")
  }

  override fun getName(): String = "context"

  override fun getFeatures(): List<String> = FEATURES

  override fun calculateFileFeatures(project: Project,
                                     newFile: VirtualFile,
                                     prevFile: VirtualFile?,
                                     cache: FilePredictionFeaturesCache): Map<String, FilePredictionFeature> {
    val result = HashMap<String, FilePredictionFeature>()
    if (!project.isDisposed) {
      result["opened"] = FilePredictionFeature.binary(FileEditorManager.getInstance(project).isFileOpen(newFile))

      if (prevFile == null || !FileEditorManager.getInstance(project).isFileOpen(prevFile)) {
        result["prev_opened"] = FilePredictionFeature.binary(false)
      }
    }
    return result
  }
}