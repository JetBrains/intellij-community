// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml.catboost

import com.intellij.internal.ml.InconsistentMetadataException
import com.intellij.internal.ml.ResourcesModelMetadataReader

class CatBoostResourcesModelMetadataReader(metadataHolder: Class<*>,
                                           featuresDirectory: String,
                                           private val modelDirectory: String) : ResourcesModelMetadataReader(metadataHolder, featuresDirectory) {

  fun loadModel(): NaiveCatBoostModel {
    val resource = "$modelDirectory/model.bin"
    val fileStream = metadataHolder.classLoader.getResourceAsStream(resource)
                     ?: throw InconsistentMetadataException(
                       "Metadata file not found: $resource. Resources holder: ${metadataHolder.name}")
    return NaiveCatBoostModel.loadModel(fileStream)
  }
}