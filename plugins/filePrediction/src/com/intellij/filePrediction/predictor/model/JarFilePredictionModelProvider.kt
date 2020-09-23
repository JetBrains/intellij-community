// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.predictor.model

import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeaturesInfo
import com.intellij.internal.ml.ModelMetadata
import com.intellij.internal.ml.ResourcesModelMetadataReader

abstract class JarFilePredictionModelProvider(private val resourceDirectory: String) : FilePredictionModelProvider {
  private val lazyModel: DecisionFunction by lazy {
    val metadata = FeaturesInfo.buildInfo(ResourcesModelMetadataReader(this::class.java, resourceDirectory))
    createModel(metadata)
  }

  protected abstract fun createModel(metadata: ModelMetadata): DecisionFunction

  override fun getModel(): DecisionFunction = lazyModel
}