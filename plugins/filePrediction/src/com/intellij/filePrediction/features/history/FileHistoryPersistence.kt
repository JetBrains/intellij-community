// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history

import com.intellij.filePrediction.features.history.ngram.FilePredictionNGramModelRunner
import com.intellij.filePrediction.features.history.ngram.FilePredictionNGramSerializer
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.PathUtil
import com.intellij.util.io.exists
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

object FileHistoryPersistence {
  private val LOG: Logger = Logger.getInstance(FileHistoryPersistence::class.java)

  private const val HISTORY_FILE_NAME_SUFFIX = ".xml"
  private const val NGRAM_FILE_NAME_SUFFIX = "-ngram"

  fun saveFileHistory(project: Project, state: FilePredictionHistoryState) {
    val path: Path? = getPathToStorage(project, HISTORY_FILE_NAME_SUFFIX)
    try {
      if (path != null) {
        JDOMUtil.write(state.serialize(), path)
      }
    }
    catch (e: IOException) {
      LOG.warn("Cannot serialize opened files history", e)
    }
  }

  fun loadFileHistory(project: Project): FilePredictionHistoryState {
    val state = FilePredictionHistoryState()
    if (project.isDisposed) {
      return state
    }

    val path: Path? = getPathToStorage(project, HISTORY_FILE_NAME_SUFFIX)
    try {
      if (path != null && path.exists()) {
        state.deserialize(JDOMUtil.load(path))
      }
    }
    catch (e: Exception) {
      LOG.warn("Cannot deserialize opened files history", e)
    }
    return state
  }

  fun saveNGrams(project: Project, runner: FilePredictionNGramModelRunner) {
    val path: Path? = getPathToStorage(project, NGRAM_FILE_NAME_SUFFIX)
    try {
      if (path != null) {
        FilePredictionNGramSerializer.saveNGrams(path, runner)
      }
    }
    catch (e: IOException) {
      LOG.warn("Cannot serialize opened files history", e)
    }
  }

  fun loadNGrams(project: Project, nGramLength: Int): FilePredictionNGramModelRunner {
    if (project.isDisposed) {
      return FilePredictionNGramSerializer.loadNGrams(null, nGramLength)
    }

    val path: Path? = getPathToStorage(project, NGRAM_FILE_NAME_SUFFIX)
    return FilePredictionNGramSerializer.loadNGrams(path, nGramLength)
  }

  private fun getPathToStorage(project: Project, suffix: String): Path? {
    val url = project.presentableUrl ?: return null
    val projectPath = Paths.get(VirtualFileManager.extractPath(url))
    val dirName = projectPath.fileName?.toString() ?: projectPath.toString().substring(0, 1)
    val storageName = PathUtil.suggestFileName(dirName + Integer.toHexString(projectPath.toString().hashCode()))
    return Paths.get(PathManager.getSystemPath(), "fileHistory", "${storageName}${suffix}")
  }
}