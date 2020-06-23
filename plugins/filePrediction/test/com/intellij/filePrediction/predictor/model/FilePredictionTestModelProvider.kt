package com.intellij.filePrediction.predictor.model

import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeatureMapper

class TestFilePredictionModelProvider(private val predict: (DoubleArray?) -> Double) : FilePredictionModelProvider {
  override fun getModel(): DecisionFunction {
    return TestFilePredictionDecisionFunction(predict)
  }
}

class TestFilePredictionDecisionFunction(private val predictor: (DoubleArray?) -> Double) : DecisionFunction {
  override fun predict(features: DoubleArray?): Double = predictor.invoke(features)

  override fun version(): String? = null

  override fun getRequiredFeatures(): MutableList<String> = arrayListOf()

  override fun getFeaturesOrder(): Array<FeatureMapper> = emptyArray()

  override fun getUnknownFeatures(features: MutableCollection<String>): MutableList<String> = arrayListOf()
}