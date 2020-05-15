// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history

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

  fun saveFileHistory(project: Project, state: FilePredictionHistoryState) {
    val path: Path? = getPathToStorage(project)
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

    val path: Path? = getPathToStorage(project)
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

  private fun getPathToStorage(project: Project): Path? {
    val url = project.presentableUrl ?: return null
    val projectPath = Paths.get(VirtualFileManager.extractPath(url))
    val dirName = projectPath.fileName?.toString() ?: projectPath.toString().substring(0, 1)
    val storageName = PathUtil.suggestFileName(dirName + Integer.toHexString(projectPath.toString().hashCode()))
    return Paths.get(PathManager.getSystemPath(), "fileHistory", "$storageName.xml")
  }
}