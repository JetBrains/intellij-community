package com.intellij.filePrediction

open class FilePredictionComputationResult<T>(val value: T, val start: Long) {
  val duration: Long = System.currentTimeMillis() - start
}

internal class FileReferencesComputationResult(references: ExternalReferencesResult, start: Long):
  FilePredictionComputationResult<ExternalReferencesResult>(references, start)

internal class FileFeaturesComputationResult(features: Map<String, FilePredictionFeature>, start: Long):
  FilePredictionComputationResult<Map<String, FilePredictionFeature>>(features, start)