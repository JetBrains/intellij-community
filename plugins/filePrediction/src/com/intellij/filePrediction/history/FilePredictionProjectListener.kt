package com.intellij.filePrediction.history

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

class FilePredictionProjectListener : ProjectManagerListener {
  override fun projectClosing(project: Project) {
    FilePredictionHistory.getInstanceIfCreated(project)?.saveFilePredictionHistory(project)
  }
}