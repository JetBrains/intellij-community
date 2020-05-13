package com.intellij.filePrediction.history.context

import com.intellij.filePrediction.FilePredictionFeature
import com.intellij.filePrediction.FilePredictionFeatureProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class FilePredictionContextFeatures: FilePredictionFeatureProvider {
  override fun getName(): String = "context"

  override fun calculateFileFeatures(project: Project, newFile: VirtualFile, prevFile: VirtualFile?): Map<String, FilePredictionFeature> {
    val result = HashMap<String, FilePredictionFeature>()
    result["opened"] = FilePredictionFeature.binary(FilePredictionContext.getInstance(project).isFileOpened(newFile.url))
    return result
  }
}