package com.intellij.filePrediction.predictor.model

import com.intellij.internal.ml.DecisionFunction

interface FilePredictionModelProvider {
  fun getModel(): DecisionFunction
}