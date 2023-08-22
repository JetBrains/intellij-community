// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.predict

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.changeReminder.repository.Commit
import com.jetbrains.changeReminder.repository.FilesHistoryProvider
import git4idea.history.GitHistoryTraverser

internal class PredictionRequest(
  private val filesHistoryProvider: FilesHistoryProvider,
  private val changeListFilesFromRoots: Map<GitHistoryTraverser.IndexedRoot, Collection<FilePath>>
) {
  private val changeListFiles = changeListFilesFromRoots.values.flatten().toSet()

  private fun getPredictedFiles(files: Collection<FilePath>, indexedRoot: GitHistoryTraverser.IndexedRoot): Collection<VirtualFile> =
    PredictionProvider(minProb = Registry.doubleValue("vcs.changeReminder.prediction.threshold"))
      .predictForgottenFiles(Commit(-1,
                                    System.currentTimeMillis(),
                                    filesHistoryProvider.traverser.getCurrentUser(indexedRoot.root)?.name ?: "",
                                    changeListFiles),
                             filesHistoryProvider.getFilesHistory(indexedRoot, files))

  private fun getPredictedFiles(): Collection<VirtualFile> =
    changeListFilesFromRoots.map { (root, files) ->
      getPredictedFiles(files, root)
    }.flatten()

  fun calculate(): PredictionData.Prediction = PredictionData.Prediction(
    requestedFiles = changeListFiles,
    prediction = getPredictedFiles()
  )
}