// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.predict

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.jetbrains.changeReminder.prediction.model.PredictionModel
import com.jetbrains.changeReminder.repository.Commit
import kotlin.math.max
import kotlin.math.min

object ProbabilityProvider {
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

    factors[Factor.MAX_INTERSECTION.ordinal] = intersectionCounters.values.maxByOrNull { it.sum }?.sum ?: 0.0
    factors[Factor.SUM_INTERSECTION.ordinal] = intersectionCounters.values.sumOf { it.sum }
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

  fun predict(commit: Commit, candidateFile: FilePath, candidateFileHistory: Set<Commit>): Double {
    return PredictionModel.makePrediction(collectFileFactors(commit, candidateFile, candidateFileHistory))
  }
}