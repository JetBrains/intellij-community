// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.predictor

import com.intellij.filePrediction.candidates.FilePredictionCandidateSource
import com.intellij.filePrediction.features.FilePredictionFeature
import com.intellij.openapi.util.NlsSafe

data class FilePredictionCandidate(
  @NlsSafe val path: String,
  val source: FilePredictionCandidateSource,
  val features: Map<String, FilePredictionFeature>,
  val featuresComputation: Long,
  val duration: Long? = null,
  val probability: Double? = null
)