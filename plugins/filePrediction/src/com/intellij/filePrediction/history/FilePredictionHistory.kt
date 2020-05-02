// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.history

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project

class FilePredictionHistory(project: Project) {
  companion object {
    private const val RECENT_FILES_LIMIT = 50

    internal fun getInstanceIfCreated(project: Project) = project.serviceIfCreated<FilePredictionHistory>()

    fun getInstance(project: Project) = project.service<FilePredictionHistory>()
  }

  private var manager: FileHistoryManager

  init {
    manager = FileHistoryManager(FileHistoryPersistence.loadFileHistory(project), RECENT_FILES_LIMIT)
  }

  fun saveFilePredictionHistory(project: Project) {
    ApplicationManager.getApplication().executeOnPooledThread {
      FileHistoryPersistence.saveFileHistory(project, manager.getState())
    }
  }

  fun onFileOpened(fileUrl: String) = manager.onFileOpened(fileUrl)

  fun calcHistoryFeatures(fileUrl: String) = manager.calcHistoryFeatures(fileUrl)

  fun size() = manager.size()

  fun cleanup() = manager.cleanup()
}