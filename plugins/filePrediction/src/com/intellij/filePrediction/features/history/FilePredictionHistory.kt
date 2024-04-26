// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.filePrediction.features.history

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
internal class FilePredictionHistory(project: Project) {
  companion object {
    private const val MAX_NGRAM_SEQUENCE = 3

    internal fun getInstanceIfCreated(project: Project) = project.serviceIfCreated<FilePredictionHistory>()

    fun getInstance(project: Project): FilePredictionHistory = project.service()
  }

  private var manager: FileHistoryManager = FileHistoryManager(FileHistoryPersistence.loadNGrams(project, MAX_NGRAM_SEQUENCE))

  init {
    FileHistoryPersistence.deleteLegacyFile(project)
  }

  fun saveFilePredictionHistory(project: Project) {
    manager.saveFileHistoryAsync(project)
  }

  fun onFileSelected(fileUrl: String) = manager.onFileOpened(fileUrl)

  fun batchCalculateNGrams(candidates: List<String>) = manager.calcNGramFeatures(candidates)
}