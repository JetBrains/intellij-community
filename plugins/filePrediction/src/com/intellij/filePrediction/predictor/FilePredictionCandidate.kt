package com.intellij.filePrediction.predictor

import com.intellij.filePrediction.FileFeaturesComputationResult

class FilePredictionCandidate(val features: FileFeaturesComputationResult, val path: String, val probability: Double? = null)