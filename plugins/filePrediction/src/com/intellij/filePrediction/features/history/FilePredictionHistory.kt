// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project

class FilePredictionHistory(project: Project) {
  companion object {
    private const val MAX_NGRAM_SEQUENCE = 3
    private const val RECENT_FILES_LIMIT = 50

    internal fun getInstanceIfCreated(project: Project) = project.serviceIfCreated<FilePredictionHistory>()

    fun getInstance(project: Project) = project.service<FilePredictionHistory>()
  }

  private var manager: FileHistoryManager

  init {
    val model = FileHistoryPersistence.loadNGrams(project, MAX_NGRAM_SEQUENCE)
    manager = FileHistoryManager(model, FileHistoryPersistence.loadFileHistory(project), RECENT_FILES_LIMIT)
  }

  fun saveFilePredictionHistory(project: Project) {
    ApplicationManager.getApplication().executeOnPooledThread {
      manager.saveFileHistory(project)
    }
  }

  fun onFileSelected(fileUrl: String) = manager.onFileOpened(fileUrl)

  fun calcHistoryFeatures(fileUrl: String) = manager.calcHistoryFeatures(fileUrl)

  fun batchCalculateNGrams(candidates: List<String>) = manager.calcNGramFeatures(candidates)

  fun size() = manager.size()

  fun cleanup() = manager.cleanup()
}