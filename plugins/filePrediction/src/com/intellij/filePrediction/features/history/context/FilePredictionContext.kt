package com.intellij.filePrediction.features.history.context

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

class FilePredictionContext {
  companion object {
    fun getInstance(project: Project) = project.service<FilePredictionContext>()
  }

  private var currentFile: String? = null
  private val openedFiles: MutableSet<String> = hashSetOf()

  @Synchronized
  fun onFileSelected(fileUrl: String) {
    onFileOpened(fileUrl)
  }

  @Synchronized
  fun onFileOpened(fileUrl: String) {
    if (fileUrl == currentFile) return

    currentFile?.let {
      if (!isFileOpened(it)) {
        openedFiles.add(it)
      }
    }
    currentFile = fileUrl
  }

  @Synchronized
  fun onFileClosed(fileUrl: String) {
    if (currentFile == fileUrl) {
      currentFile = null
    }
    openedFiles.remove(fileUrl)
  }

  @Synchronized
  fun isFileOpened(fileUrl: String): Boolean = openedFiles.contains(fileUrl)
}