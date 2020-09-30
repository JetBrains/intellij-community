// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.predict

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.changeReminder.repository.Commit

/**
 * This class provides a prediction about forgotten (to commit or modify) files.
 * Plugin uses the VCS repository history and ML algorithms to make the predictions.
 */
class PredictionProvider(private val minProb: Double = 0.55) {
  companion object {
    private const val MAX_RELATED_FILES_COUNT = 150
  }


  private fun getRelatedFiles(commit: Commit, history: Collection<Commit>): Map<FilePath, Set<Commit>> {
    val sortedHistory = history.sortedByDescending { it.time }
    val relatedFiles = mutableMapOf<FilePath, MutableSet<Commit>>()

    for (oldCommit in sortedHistory) {
      if (relatedFiles.size > MAX_RELATED_FILES_COUNT) {
        break
      }
      oldCommit.files.filter { it !in commit.files }.forEach { relatedFile ->
        relatedFiles.getOrPut(relatedFile) { mutableSetOf() }.add(oldCommit)
      }
    }

    return relatedFiles
  }

  /**
   * Method makes a prediction about forgotten files, which are close to files contained in commits from the [history].
   *
   * @param commit commit for which system tries to predict files
   * @param history commits which system uses to predict files
   * @param maxPredictedFileCount maximum files to be predicted
   * @return list of forgotten files
   */
  fun predictForgottenFiles(commit: Commit, history: Collection<Commit>, maxPredictedFileCount: Int = 5): List<VirtualFile> =
    getRelatedFiles(commit, history)
      .asSequence()
      .map { (candidateFile, candidateFileHistory) ->
        ProgressManager.checkCanceled()
        val fileScore = ProbabilityProvider.predict(commit, candidateFile, candidateFileHistory)
        candidateFile to fileScore
      }
      .filter { it.second > minProb }
      .filter { it.first.virtualFile != null }
      .sortedByDescending { it.second }
      .take(maxPredictedFileCount)
      .mapNotNull { it.first.virtualFile }
      .toList()
}