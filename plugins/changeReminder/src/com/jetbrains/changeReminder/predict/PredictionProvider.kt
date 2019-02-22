// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.predict

import com.intellij.openapi.vcs.FilePath
import com.jetbrains.changeReminder.repository.Commit
import kotlin.math.min

/**
 * This class provides a prediction about forgotten (to commit or modify) files, based on the VCS history of given files.
 *
 * To get more information about decision function used here, see the README.md file.
 */
class PredictionProvider(private val minProb: Double = 0.3) {
  companion object {
    private const val m = 3.2
    private const val commitSize = 8.0
    private const val historyLength = 20
  }

  private class VoteProvider(private val m: Double) {
    private var votesCounter = 0.0
    private var votesSum = 0.0

    private fun R() = if (votesCounter != 0.0) votesSum / votesCounter else 0.0

    fun result(): Double {
      val v = votesCounter
      return (v / (m + v)) * R() + (m / (m + v)) * 0.25
    }

    fun vote(rate: Double) {
      votesCounter += 1.0
      votesSum += rate
    }
  }

  private fun vote(fileCommits: Set<Commit>, commitFiles: Set<FilePath>): Map<FilePath, Double> {
    val candidates = HashMap<FilePath, VoteProvider>()
    val commits = fileCommits
      .sortedBy { it.time }
      .reversed()
      .take(historyLength)
    for (fileCommit in commits) {
      for (secondFile in fileCommit.files) {
        if (secondFile in commitFiles) {
          continue
        }
        val currentRate = min(1.0, commitSize / fileCommit.files.size.toDouble())
        candidates.getOrPut(secondFile) { VoteProvider(m) }.vote(currentRate)
      }
    }

    return candidates.mapValues { it.value.result() }
  }

  /**
   * Method makes a prediction about forgotten files, which are close to files from their [filesHistory].
   *
   * @param filesHistory map from file to its previous commits
   * @param maxPredictedFileCount maximum files to be predicted
   * @return list of forgotten files
   */
  fun predictForgottenFiles(filesHistory: Map<FilePath, Set<Commit>>, maxPredictedFileCount: Int = 5): List<FilePath> {
    val candidates = HashMap<FilePath, Double>()

    for (commits in filesHistory.values) {
      val currentVotes = vote(commits, filesHistory.keys)
      for ((currentFile, currentVote) in currentVotes) {
        candidates.merge(currentFile, currentVote, Double::plus)
      }
    }

    return candidates
      .mapValues { it.value / filesHistory.size }
      .filterValues { it > minProb }
      .toList()
      .sortedByDescending { it.second }
      .take(maxPredictedFileCount)
      .map { it.first }
  }
}