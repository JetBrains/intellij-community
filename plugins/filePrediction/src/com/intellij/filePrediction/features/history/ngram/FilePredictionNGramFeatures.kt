// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history.ngram

import com.intellij.filePrediction.features.history.NextFileProbability
import kotlin.math.max
import kotlin.math.min

class FilePredictionNGramFeatures(private val byFile: Map<String, Double>) {
  private val minProbability: Double
  private val maxProbability: Double

  init {
    var minProb = 1.0
    var maxProb = 0.0
    for (nGram in byFile.values) {
      minProb = min(minProb, nGram)
      maxProb = max(maxProb, nGram)
    }
    minProbability = max(minProb, 0.0001)
    maxProbability = max(maxProb, 0.0001)
  }

  fun calculateFileFeatures(fileUrl: String): NextFileProbability? {
    val probability = byFile[fileUrl]
    if (probability == null) {
      return null
    }

    val mleToMin = if (minProbability != 0.0) probability / minProbability else 0.0
    val mleToMax = if (maxProbability != 0.0) probability / maxProbability else 0.0
    return NextFileProbability(probability, minProbability, maxProbability, mleToMin, mleToMax)
  }
}