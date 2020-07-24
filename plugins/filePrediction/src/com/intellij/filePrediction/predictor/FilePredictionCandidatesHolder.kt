// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.predictor

import com.intellij.filePrediction.features.FilePredictionFeature
import com.intellij.filePrediction.features.FilePredictionFeaturesHelper
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap

interface FilePredictionCandidatesHolder {
  fun getCandidates(): List<FilePredictionCandidate>
}

internal class FilePredictionCompressedCandidatesHolder(
  private val candidates: List<FilePredictionCompressedCandidate>,
  private val codes: Map<Int, String>
) : FilePredictionCandidatesHolder {
  companion object {
    fun create(candidates: List<FilePredictionCandidate>): FilePredictionCompressedCandidatesHolder {
      val codes = FilePredictionFeaturesHelper.getFeatureCodes()
      val encodedCandidates = candidates.map { encode(it, codes) }
      val invertedCodes = Int2ObjectOpenHashMap<String>()
      for (code in codes) {
        invertedCodes[code.value] = code.key
      }
      return FilePredictionCompressedCandidatesHolder(encodedCandidates, invertedCodes)
    }

    private fun encode(candidate: FilePredictionCandidate, codes: Map<String, Int>): FilePredictionCompressedCandidate {
      val features = Int2ObjectOpenHashMap<FilePredictionFeature>()
      for (feature in candidate.features) {
        val newKey = codes[feature.key]
        if (newKey != null) {
          features[newKey] = feature.value
        }
      }

      return FilePredictionCompressedCandidate(
        candidate.path, candidate.source, features,
        candidate.featuresComputation, candidate.duration, candidate.probability
      )
    }

    private fun decode(candidate: FilePredictionCompressedCandidate, codes: Map<Int, String>): FilePredictionCandidate {
      val features = candidate.features.mapKeys { codes.getOrDefault(it.key, "unknown") }
      return FilePredictionCandidate(
        candidate.path, candidate.source, features,
        candidate.featuresComputation, candidate.duration, candidate.probability
      )
    }
  }

  override fun getCandidates(): List<FilePredictionCandidate> {
    return candidates.map { decode(it, codes) }
  }
}

internal class FilePredictionCompressedCandidate(
  val path: String,
  val source: String,
  val features: Map<Int, FilePredictionFeature>,
  val featuresComputation: Long,
  val duration: Long? = null,
  val probability: Double? = null
)