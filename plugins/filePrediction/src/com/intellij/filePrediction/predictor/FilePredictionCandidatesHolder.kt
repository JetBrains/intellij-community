// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.predictor

import com.intellij.filePrediction.candidates.FilePredictionCandidateSource
import com.intellij.filePrediction.features.FilePredictionFeaturesHelper

interface FilePredictionCandidatesHolder {
  fun getCandidates(): List<FilePredictionCompressedCandidate>
}

internal class FilePredictionCompressedCandidatesHolder(
  private val candidates: List<FilePredictionCompressedCandidate>
) : FilePredictionCandidatesHolder {
  companion object {
    fun create(candidates: List<FilePredictionCandidate>): FilePredictionCompressedCandidatesHolder {
      val codes = FilePredictionFeaturesHelper.getFeaturesByProviders()
      val encodedCandidates = candidates.map { encode(it, codes) }
      return FilePredictionCompressedCandidatesHolder(encodedCandidates)
    }

    private fun encode(candidate: FilePredictionCandidate, codesByProviders: List<List<String>>): FilePredictionCompressedCandidate {
      val features = candidate.features
      val compressed: Array<Array<Any?>> = Array(codesByProviders.size) {
        return@Array codesByProviders[it].map { code -> features[code]?.value }.toTypedArray()
      }

      return FilePredictionCompressedCandidate(
        candidate.path, candidate.source, compressed,
        candidate.featuresComputation, candidate.duration, candidate.probability
      )
    }
  }

  override fun getCandidates(): List<FilePredictionCompressedCandidate> {
    return candidates
  }
}

class FilePredictionCompressedCandidate(
  val path: String,
  val source: FilePredictionCandidateSource,
  val features: Array<Array<Any?>>,
  val featuresComputation: Long,
  val duration: Long? = null,
  val probability: Double? = null
)