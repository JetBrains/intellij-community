package com.intellij.filePrediction.predictor.model

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import junit.framework.TestCase

private val EP_NAME = ExtensionPointName<FilePredictionModelProvider>("com.intellij.filePrediction.ml.model")

internal fun disableFilePredictionModel() {
  setCustomTestFilePredictionModel(null, null)
}

internal fun setPredefinedProbabilityModel(disposable: Disposable?, probabilities: List<Double>) {
  var ind = 0
  setCustomTestFilePredictionModel(disposable) {
    val next = probabilities[ind]
    ind += 1
    next
  }
}

internal fun setConstantFilePredictionModel(probability: Double, disposable: Disposable?) {
  setCustomTestFilePredictionModel(disposable) { probability }
}

internal fun setCustomTestFilePredictionModel(disposable: Disposable?, predictor: (DoubleArray?) -> Double) {
  setCustomTestFilePredictionModel(disposable, TestFilePredictionModelProvider(predictor))
}

private fun setCustomTestFilePredictionModel(disposable: Disposable? = null, model: FilePredictionModelProvider? = null) {
  val ep: ExtensionPoint<FilePredictionModelProvider> = Extensions.getRootArea().getExtensionPoint(EP_NAME)
  for (extension in EP_NAME.extensions) {
    ep.unregisterExtension(extension.javaClass)
  }

  if (model != null) {
    TestCase.assertNotNull("Cannot register custom model because disposable is null", disposable)
    ep.registerExtension(model, disposable!!)
  }
}