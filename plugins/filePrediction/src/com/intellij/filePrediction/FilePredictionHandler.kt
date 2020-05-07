package com.intellij.filePrediction

import com.intellij.filePrediction.history.FilePredictionHistory
import com.intellij.filePrediction.predictor.FileUsagePredictor
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.NonUrgentExecutor

class FilePredictionHandler {
  companion object {
    private const val CALCULATE_OPEN_FILE_PROBABILITY: Double = 0.5
    private const val CALCULATE_CANDIDATE_PROBABILITY: Double = 0.1

    fun getInstance(): FilePredictionHandler? = ServiceManager.getService(FilePredictionHandler::class.java)
  }

  private val predictor: FileUsagePredictor = FileUsagePredictor(30, 5, 10)

  private var session: FilePredictionSessionHolder = FilePredictionSessionHolder()

  fun onFileOpened(project: Project, newFile: VirtualFile, prevFile: VirtualFile?) {
    if (ProjectManagerImpl.isLight(project)) {
      return
    }

    NonUrgentExecutor.getInstance().execute {
      BackgroundTaskUtil.runUnderDisposeAwareIndicator(project, Runnable {
        val previousSession = session.getSession()
        if (previousSession != null && previousSession.shouldLog(CALCULATE_OPEN_FILE_PROBABILITY)) {
          logOpenedFile(project, previousSession.id, newFile, prevFile)
        }
        val newSession = session.newSession()
        if (newSession != null && newSession.shouldLog(CALCULATE_CANDIDATE_PROBABILITY)) {
          predictor.predictNextFile(project, newSession.id, newFile)
        }
        FilePredictionHistory.getInstance(project).onFileOpened(newFile.url)
      })
    }
  }

  private fun logOpenedFile(project: Project,
                            sessionId: Int,
                            newFile: VirtualFile,
                            prevFile: VirtualFile?) {
    val result = FilePredictionFeaturesHelper.calculateExternalReferences(project, prevFile)

    val features = FilePredictionFeaturesHelper.calculateFileFeatures(project, newFile, result.value, prevFile)
    FileNavigationLogger.logEvent(project, "file.opened", sessionId, features, newFile.path, prevFile?.path, result.duration)
  }
}