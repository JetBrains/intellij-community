package com.intellij.filePrediction.predictor.model

import com.intellij.filePrediction.candidates.CompositeCandidateProvider
import com.intellij.filePrediction.candidates.FilePredictionCandidateProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import junit.framework.TestCase

private val EP_NAME = ExtensionPointName<FilePredictionModelProvider>("com.intellij.filePrediction.ml.model")
private val CANDIDATE_EP_NAME = ExtensionPointName<FilePredictionCandidateProvider>("com.intellij.filePrediction.candidateProvider")

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

internal fun setCustomTestFilePredictionModel(disposable: Disposable? = null, model: FilePredictionModelProvider? = null) {
  val ep: ExtensionPoint<FilePredictionModelProvider> = Extensions.getRootArea().getExtensionPoint(EP_NAME)
  for (extension in EP_NAME.extensions) {
    ep.unregisterExtension(extension.javaClass)
  }

  if (model != null) {
    TestCase.assertNotNull("Cannot register custom model because disposable is null", disposable)
    ep.registerExtension(model, disposable!!)
  }
}

internal fun setCustomCandidateProviderModel(disposable: Disposable? = null, vararg providers: FilePredictionCandidateProvider) {
  val ep: ExtensionPoint<FilePredictionCandidateProvider> = Extensions.getRootArea().getExtensionPoint(CANDIDATE_EP_NAME)
  for (extension in CANDIDATE_EP_NAME.extensions) {
    ep.unregisterExtension(extension.javaClass)
  }

  if (providers.isNotEmpty()) {
    TestCase.assertNotNull("Cannot register custom providers because disposable is null", disposable)
    ep.registerExtension(FilePredictionTestCandidateProvider(providers.toList()), disposable!!)
  }
}

internal fun unregisterCandidateProvider(provider: FilePredictionCandidateProvider) {
  val ep: ExtensionPoint<FilePredictionCandidateProvider> = Extensions.getRootArea().getExtensionPoint(CANDIDATE_EP_NAME)
  if (CANDIDATE_EP_NAME.extensions.map { it.javaClass }.contains(provider.javaClass)) {
    ep.unregisterExtension(provider.javaClass)
  }
}

private class FilePredictionTestCandidateProvider(private val providers: List<FilePredictionCandidateProvider>) : CompositeCandidateProvider() {
  override fun getProviders() : List<FilePredictionCandidateProvider> = providers
}