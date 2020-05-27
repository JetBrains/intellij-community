// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.predictor

import com.intellij.filePrediction.features.FilePredictionFeature

data class FilePredictionCandidate(
  val path: String,
  val source: String,
  val features: Map<String, FilePredictionFeature>,
  val featuresComputation: Long,
  val duration: Long? = null,
  val probability: Double? = null
)