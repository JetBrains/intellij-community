// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.filePrediction.history.FilePredictionHistory
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.NonUrgentExecutor

internal object FileUsagePredictor {
  private const val CALCULATE_OPEN_FILE_PROBABILITY: Double = 0.5

  private const val CALCULATE_CANDIDATE_PROBABILITY: Double = 0.1
  private const val MAX_CANDIDATE: Int = 10

  fun onFileOpened(project: Project, newFile: VirtualFile, prevFile: VirtualFile?) {
    if (ProjectManagerImpl.isLight(project)) {
      return
    }

    NonUrgentExecutor.getInstance().execute {
      BackgroundTaskUtil.runUnderDisposeAwareIndicator(project, Runnable {
        if (Math.random() < CALCULATE_OPEN_FILE_PROBABILITY) {
          logOpenedFile(project, newFile, prevFile)
        }
        if (Math.random() < CALCULATE_CANDIDATE_PROBABILITY) {
          predictNextFile(project, newFile)
        }
        FilePredictionHistory.getInstance(project).onFileOpened(newFile.url)
      })
    }
  }

  private fun logOpenedFile(project: Project, newFile: VirtualFile, prevFile: VirtualFile?) {
    val result = FilePredictionFeaturesHelper.calculateExternalReferences(project, prevFile)

    val features = FilePredictionFeaturesHelper.calculateFileFeatures(project, newFile, result.value, prevFile)
    FileNavigationLogger.logEvent(project, "file.opened", features, newFile.path, result.duration)
  }

  private fun predictNextFile(project: Project, file: VirtualFile) {
    val result = FilePredictionFeaturesHelper.calculateExternalReferences(project, file)

    val candidates = CompositeCandidateProvider.provideCandidates(project, file, result.value.references, MAX_CANDIDATE)
    for (candidate in candidates) {
      val features = FilePredictionFeaturesHelper.calculateFileFeatures(project, candidate, result.value, file)
      FileNavigationLogger.logEvent(project, "candidate.calculated", features, candidate.path, result.duration)
    }
  }
}

