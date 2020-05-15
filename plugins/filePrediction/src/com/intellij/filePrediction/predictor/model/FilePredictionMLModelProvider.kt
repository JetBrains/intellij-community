// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.predictor.model

import com.intellij.filePrediction.model.PredictionModel
import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.ModelMetadata

class FilePredictionMLModelProvider : JarFilePredictionModelProvider("features") {
  override fun createModel(metadata: ModelMetadata): DecisionFunction {
    return object : FilePredictionDecisionFunction(metadata) {
      override fun predict(features: DoubleArray?): Double = PredictionModel.makePredict(features)
    }
  }
}