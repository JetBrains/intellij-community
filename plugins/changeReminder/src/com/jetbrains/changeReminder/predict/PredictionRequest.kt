// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.predict

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.data.VcsLogData
import com.jetbrains.changeReminder.getGitRootFiles
import com.jetbrains.changeReminder.repository.Commit
import com.jetbrains.changeReminder.repository.FilesHistoryProvider

internal class PredictionRequest(private val project: Project,
                                 private val dataManager: VcsLogData,
                                 private val filesHistoryProvider: FilesHistoryProvider,
                                 private val changeListFiles: Collection<FilePath>) {
  private fun getPredictedFiles(files: Collection<FilePath>, root: VirtualFile): Collection<VirtualFile> =
    PredictionProvider(minProb = Registry.doubleValue("vcs.changeReminder.prediction.threshold"))
      .predictForgottenFiles(Commit(-1,
                                    System.currentTimeMillis(),
                                    dataManager.currentUser[root]?.name ?: "",
                                    changeListFiles.toSet()),
                             filesHistoryProvider.getFilesHistory(root, files))

  private fun getPredictedFiles(rootFiles: Map<VirtualFile, Collection<FilePath>>): Collection<VirtualFile> =
    rootFiles.mapNotNull { (root, files) ->
      if (dataManager.index.isIndexed(root)) {
        getPredictedFiles(files, root)
      }
      else {
        null
      }
    }.flatten()

  fun calculate(): PredictionData.Prediction {
    val rootFiles = getGitRootFiles(project, changeListFiles)
    return PredictionData.Prediction(
      requestedFiles = changeListFiles,
      prediction = getPredictedFiles(rootFiles)
    )
  }
}