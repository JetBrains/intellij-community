package com.intellij.filePrediction

import com.intellij.filePrediction.history.FilePredictionHistory
import com.intellij.filePrediction.predictor.FileUsagePredictor
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.NonUrgentExecutor

object FilePredictionHandler {
  private const val CALCULATE_OPEN_FILE_PROBABILITY: Double = 0.5

  private const val CALCULATE_CANDIDATE_PROBABILITY: Double = 0.1
  private val predictor: FileUsagePredictor = FileUsagePredictor(30, 5, 10)

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
          predictor.predictNextFile(project, newFile)
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
}