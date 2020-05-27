// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.filePrediction.features.history.FilePredictionHistory
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.NonUrgentExecutor

class FilePredictionHandler : Disposable {
  companion object {
    private const val CALCULATE_CANDIDATE_PROBABILITY: Double = 0.1

    fun getInstance(): FilePredictionHandler? = ServiceManager.getService(FilePredictionHandler::class.java)
  }

  private var manager: FilePredictionSessionManager
    = FilePredictionSessionManager(50, 5, 10, CALCULATE_CANDIDATE_PROBABILITY)

  fun onFileSelected(project: Project, newFile: VirtualFile) {
    if (ProjectManagerImpl.isLight(project)) {
      return
    }

    NonUrgentExecutor.getInstance().execute {
      BackgroundTaskUtil.runUnderDisposeAwareIndicator(this, Runnable {
        manager.finishSession(project, newFile)

        FilePredictionHistory.getInstance(project).onFileSelected(newFile.url)
        manager.startSession(project, newFile)
      })
    }
  }

  override fun dispose() {
  }
}