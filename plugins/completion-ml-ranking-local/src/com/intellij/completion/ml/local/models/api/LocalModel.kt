package com.intellij.completion.ml.local.models.api

interface LocalModel {
  val featuresProvider: LocalModelFeaturesProvider
  val builder: LocalModelBuilder
}