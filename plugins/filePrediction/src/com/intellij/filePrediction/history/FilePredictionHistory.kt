// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.history

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener

class FilePredictionHistory(val project: Project) : Disposable {
  companion object {
    private const val RECENT_FILES_LIMIT = 50

    fun getInstance(project: Project): FilePredictionHistory {
      return ServiceManager.getService(project, FilePredictionHistory::class.java)
    }
  }

  private var manager: FileHistoryManager

  init {
    manager = FileHistoryManager(FileHistoryPersistence.loadFileHistory(project), RECENT_FILES_LIMIT)

    project.messageBus.connect(this).subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      override fun projectClosing(project: Project) {
        ApplicationManager.getApplication().executeOnPooledThread {
          FileHistoryPersistence.saveFileHistory(project, manager.getState())
        }
      }
    })
  }

  fun onFileOpened(fileUrl: String) = manager.onFileOpened(fileUrl)

  fun calcHistoryFeatures(fileUrl: String): FileHistoryFeatures = manager.calcHistoryFeatures(fileUrl)

  fun size(): Int = manager.size()

  fun cleanup() = manager.cleanup()

  override fun dispose() {
  }
}