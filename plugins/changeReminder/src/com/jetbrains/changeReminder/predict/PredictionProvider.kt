// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.predict

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.jetbrains.changeReminder.prediction.model.MLWhiteBox
import com.jetbrains.changeReminder.repository.Commit
import kotlin.math.max
import kotlin.math.min

/**
 * This class provides a prediction about forgotten (to commit or modify) files, based on the VCS history of given files.
 *
 * To get more information about decision function used here, see the README.md file.
 */
class PredictionProvider(private val minProb: Double = 0.3) {
  companion object {
    private const val HISTORY_DEPTH = 20
    private const val MAX_RELATED_FILES_COUNT = 150
    private const val MAX_HISTORY_COMMIT_SIZE = 15
    private const val DEFAULT_MIN_VALUE = 470_000_000_000.0
  }

  private class FactorCounter {
    var sum = 0.0
      private set
    var max = 0.0
      private set
    var min = DEFAULT_MIN_VALUE
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
      val filesFromCommit = oldCommit.files.filter { file -> file in newCommit.files }

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


  private fun getRelatedFiles(sortedFilesHistory: Map<FilePath, List<Commit>>): Map<FilePath, Set<Commit>> {
    val relatedFiles = mutableMapOf<FilePath, MutableSet<Commit>>()
    val commitFiles = sortedFilesHistory.keys

    for (depth in 0 until HISTORY_DEPTH) {
      if (relatedFiles.size > MAX_RELATED_FILES_COUNT) {
        break
      }
      sortedFilesHistory.values.forEach { commits ->
        if (depth < commits.size) {
          val oldCommit = commits[depth]
          if (oldCommit.files.size > MAX_HISTORY_COMMIT_SIZE) {
            oldCommit.files.filter { it !in commitFiles }.forEach { relatedFile ->
              relatedFiles.getOrPut(relatedFile) { mutableSetOf() }.add(oldCommit)
            }
          }
        }
      }
    }

    return relatedFiles
  }

  /**
   * Method makes a prediction about forgotten files, which are close to files from their [filesHistory].
   *
   * @param commit commit for which system try to predict files
   * @param filesHistory map from file from commit to its previous commits
   * @param maxPredictedFileCount maximum files to be predicted
   * @return list of forgotten files
   */
  fun predictForgottenFiles(commit: Commit, filesHistory: Map<FilePath, Set<Commit>>, maxPredictedFileCount: Int = 5): List<FilePath> =
    getRelatedFiles(filesHistory.mapValues { it.value.sortedByDescending { commit -> commit.time } })
      .asSequence()
      .map { (candidateFile, candidateFileHistory) ->
        val fileScore = MLWhiteBox.makePredict(collectFileFactors(commit, candidateFile, candidateFileHistory))
        candidateFile to fileScore
      }
      .filter { it.second > minProb }
      .sortedByDescending { it.second }
      .take(maxPredictedFileCount)
      .map { it.first }
      .toList()
}