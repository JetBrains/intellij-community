// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.history

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@State(name = "FilePredictionHistory", storages = [Storage(StoragePathMacros.CACHE_FILE)])
class FilePredictionHistory: PersistentStateComponent<FilePredictionHistoryState> {
  private var state = FilePredictionHistoryState()

  companion object {
    private const val RECENT_FILES_LIMIT = 50

    fun getInstance(project: Project): FilePredictionHistory {
      return ServiceManager.getService(project, FilePredictionHistory::class.java)
    }
  }

  fun onFileOpened(fileUrl: String) = state.onFileOpened(fileUrl, RECENT_FILES_LIMIT)

  fun position(fileUrl: String): Int = state.position(fileUrl)

  fun size(): Int = state.size()

  fun cleanup() = state.cleanup()

  override fun getState(): FilePredictionHistoryState? {
    return state
  }

  override fun loadState(newState: FilePredictionHistoryState) {
    state = newState
  }
}