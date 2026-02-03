// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.filePrediction.features.FilePredictionFeature
import com.intellij.filePrediction.references.ExternalReferencesResult

open class FilePredictionComputationResult<T>(val value: T, private val start: Long) {
  val duration: Long = System.currentTimeMillis() - start
}

internal class FileReferencesComputationResult(references: ExternalReferencesResult, start: Long):
  FilePredictionComputationResult<ExternalReferencesResult>(references, start)

class FileFeaturesComputationResult(features: Map<String, FilePredictionFeature>, start: Long):
  FilePredictionComputationResult<Map<String, FilePredictionFeature>>(features, start)