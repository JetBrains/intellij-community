// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history.context

import com.intellij.filePrediction.features.FilePredictionFeature
import com.intellij.filePrediction.features.FilePredictionFeatureProvider
import com.intellij.filePrediction.references.ExternalReferencesResult
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class FilePredictionContextFeatures: FilePredictionFeatureProvider {
  override fun getName(): String = "context"

  override fun getFeatures(): Array<String> = arrayOf("opened")

  override fun calculateFileFeatures(project: Project,
                                     newFile: VirtualFile,
                                     prevFile: VirtualFile?,
                                     refs: ExternalReferencesResult): Map<String, FilePredictionFeature> {
    val result = HashMap<String, FilePredictionFeature>()
    result["opened"] = FilePredictionFeature.binary(FileEditorManager.getInstance(project).isFileOpen(newFile))
    return result
  }
}