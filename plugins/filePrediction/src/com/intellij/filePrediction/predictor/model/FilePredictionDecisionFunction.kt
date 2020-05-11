package com.intellij.filePrediction.predictor.model

import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeatureMapper
import com.intellij.internal.ml.ModelMetadata

abstract class FilePredictionDecisionFunction(private val metadata: ModelMetadata) : DecisionFunction {
  override fun getFeaturesOrder(): Array<FeatureMapper> = metadata.featuresOrder

  override fun version(): String? = metadata.version

  override fun getRequiredFeatures(): List<String> = emptyList()

  override fun getUnknownFeatures(features: Collection<String>): List<String> = emptyList()
}