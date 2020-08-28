// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.predictor

import com.intellij.filePrediction.candidates.FilePredictionCandidateSource
import com.intellij.filePrediction.features.FilePredictionFeature
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
      val encoded = encodeFeatures(candidate.features, codesByProviders)
      return FilePredictionCompressedCandidate(
        candidate.path, candidate.source, encoded,
        candidate.featuresComputation, candidate.duration, candidate.probability
      )
    }
    
    internal fun encodeFeatures(features: Map<String, FilePredictionFeature>, codesByProviders: List<List<String>>): String {
      val result = StringBuilder()
      for ((providerIndex, codesByProvider) in codesByProviders.withIndex()) {
        if (providerIndex > 0) result.append(';')
        for ((featureIndex, code) in codesByProvider.withIndex()) {
          if (featureIndex > 0) result.append(',')
          features[code]?.appendTo(result)
        }
      }
      return result.toString()
    }
  }

  override fun getCandidates(): List<FilePredictionCompressedCandidate> {
    return candidates
  }
}

class FilePredictionCompressedCandidate(
  val path: String,
  val source: FilePredictionCandidateSource,
  val features: String,
  val featuresComputation: Long,
  val duration: Long? = null,
  val probability: Double? = null
)