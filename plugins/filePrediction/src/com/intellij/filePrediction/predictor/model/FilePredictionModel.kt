// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.predictor.model

import com.intellij.filePrediction.features.FilePredictionFeature
import com.intellij.internal.ml.FeatureMapper
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName

private val LOG = Logger.getInstance("#com.intellij.filePrediction.predictor.model")
private val EP_NAME = ExtensionPointName<FilePredictionModelProvider>("com.intellij.filePrediction.ml.model")

class FilePredictionModel(private val provider: FilePredictionModelProvider) {
  fun predict(features: Map<String, FilePredictionFeature>): Double {
    val model = provider.getModel()
    return model.predict(buildArray(model.featuresOrder, features))
  }

  private fun buildArray(featuresOrder: Array<FeatureMapper>, features: Map<String, FilePredictionFeature>): DoubleArray {
    val array = DoubleArray(featuresOrder.size)
    for (i in featuresOrder.indices) {
      val mapper = featuresOrder[i]
      val value = features[mapper.featureName]
      array[i] = mapper.asArrayValue(value?.value)
    }
    return array
  }
}

internal fun getFilePredictionModel(): FilePredictionModel? {
  val extensions = EP_NAME.extensions
  if (extensions.size > 1) {
    LOG.warn("Multiple file prediction models are registered")
  }
  return if (extensions.isNotEmpty()) FilePredictionModel(extensions[0]) else null
}