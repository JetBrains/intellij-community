// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.predict

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.changeReminder.prediction.model.PredictionModel
import com.jetbrains.changeReminder.repository.Commit
import kotlin.math.max
import kotlin.math.min

/**
 * This class provides a prediction about forgotten (to commit or modify) files.
 * Plugin uses the VCS repository history and ML algorithms to make the predictions.
 */
class PredictionProvider(private val minProb: Double = 0.55) {
  companion object {
    private const val MAX_RELATED_FILES_COUNT = 150
    private const val MAX_HISTORY_COMMIT_SIZE = 15
  }

  private class FactorCounter {
    var sum = 0.0
      private set
    var max = 0.0
      private set
    var min = Double.MAX_VALUE
      private set

    fun append(newValue: Number): FactorCounter {
      return append(newValue.toDouble())
    }

    private fun append(newValue: Double): FactorCounter {
      sum += newValue
      max = max(max, newValue)
      min = min(min, newValue)

      return this
    }
  }

  private fun collectFileFactors(newCommit: Commit, candidateFile: FilePath, candidateFileHistory: Set<Commit>): DoubleArray {
    val factors = DoubleArray(Factor.values().size)

    val timeDistanceCounter = FactorCounter()
    val intersectionCounters = mutableMapOf<FilePath, FactorCounter>()
    val countFilesCounter = FactorCounter()
    val filePrefixCounter = FactorCounter()
    val pathPrefixCounter = FactorCounter()
    var authorCommitted = 0.0

    candidateFileHistory.forEach { oldCommit ->
      val filesFromCommit = oldCommit.files.intersect(newCommit.files)

      countFilesCounter.append(filesFromCommit.size)
      if (filesFromCommit.isNotEmpty()) {
        timeDistanceCounter.append(newCommit.time - oldCommit.time)
      }

      if (authorCommitted != 1.0 && oldCommit.author.startsWith(newCommit.author)) {
        authorCommitted = 1.0
      }

      filesFromCommit.forEach { file ->
        intersectionCounters.getOrPut(file) { FactorCounter() }.append(1)
      }
    }

    val candidateFilePath = candidateFile.path
    val candidateFileName = candidateFile.name
    newCommit.files.forEach {
      pathPrefixCounter.append(StringUtil.commonPrefixLength(candidateFilePath, it.path))
      filePrefixCounter.append(StringUtil.commonPrefixLength(candidateFileName, it.name))
    }

    factors[Factor.MAX_INTERSECTION.ordinal] = intersectionCounters.values.maxBy { it.sum }?.sum ?: 0.0
    factors[Factor.SUM_INTERSECTION.ordinal] = intersectionCounters.values.sumByDouble { it.sum }
    factors[Factor.MIN_DISTANCE_TIME.ordinal] = timeDistanceCounter.min
    factors[Factor.COMMIT_SIZE.ordinal] = newCommit.files.size.toDouble()
    factors[Factor.MAX_DISTANCE_TIME.ordinal] = timeDistanceCounter.max
    factors[Factor.AVG_DISTANCE_TIME.ordinal] = timeDistanceCounter.sum / candidateFileHistory.size.toDouble()
    factors[Factor.MAX_COUNT.ordinal] = countFilesCounter.max
    factors[Factor.MIN_COUNT.ordinal] = countFilesCounter.min
    factors[Factor.AUTHOR_COMMITTED_THE_FILE.ordinal] = authorCommitted
    factors[Factor.MAX_PREFIX_PATH.ordinal] = pathPrefixCounter.max
    factors[Factor.MAX_PREFIX_FILE_NAME.ordinal] = filePrefixCounter.max

    return factors
  }


  private fun getRelatedFiles(commit: Commit, history: Collection<Commit>): Map<FilePath, Set<Commit>> {
    val sortedHistory = history.sortedByDescending { it.time }
    val relatedFiles = mutableMapOf<FilePath, MutableSet<Commit>>()

    for (oldCommit in sortedHistory) {
      if (relatedFiles.size > MAX_RELATED_FILES_COUNT) {
        break
      }
      if (oldCommit.files.size < MAX_HISTORY_COMMIT_SIZE) {
        oldCommit.files.filter { it !in commit.files }.forEach { relatedFile ->
          relatedFiles.getOrPut(relatedFile) { mutableSetOf() }.add(oldCommit)
        }
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
        val fileScore = PredictionModel.makePrediction(collectFileFactors(commit, candidateFile, candidateFileHistory))
        candidateFile to fileScore
      }
      .filter { it.second > minProb }
      .filter { it.first.virtualFile != null }
      .sortedByDescending { it.second }
      .take(maxPredictedFileCount)
      .mapNotNull { it.first.virtualFile }
      .toList()
}