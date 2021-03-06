// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.filePrediction.features.history.FilePredictionHistory
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.SequentialTaskExecutor

class FilePredictionHandler(private val project: Project) : Disposable {
  companion object {
    private val LOG: Logger = Logger.getInstance(FilePredictionHandler::class.java)

    fun getInstance(project: Project): FilePredictionHandler? = ServiceManager.getService(project, FilePredictionHandler::class.java)
  }

  private val executor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("NextFilePrediction")
  private val manager: FilePredictionSessionManager

  init {
    val percent = Registry.get("filePrediction.calculate.candidates.percent").asDouble()
    manager = FilePredictionSessionManager(125, 3, 5, percent)
  }

  fun onFileSelected(newFile: VirtualFile) {
    if (ProjectManagerImpl.isLight(project)) {
      return
    }

    executor.submit {
      BackgroundTaskUtil.runUnderDisposeAwareIndicator(this, Runnable {
        val start = System.currentTimeMillis()
        FilePredictionHistory.getInstance(project).onFileSelected(newFile.url)

        manager.onSessionStarted(project, newFile)
        if (LOG.isTraceEnabled) {
          LOG.trace("Candidates calculation took ${System.currentTimeMillis() - start}ms")
        }
      })
    }
  }

  override fun dispose() {
    executor.shutdown()
  }
}