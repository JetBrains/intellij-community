package com.jetbrains.completion.ml.ranker.cb.jvm

import com.intellij.internal.ml.InconsistentMetadataException
import com.intellij.internal.ml.ResourcesModelMetadataReader

class NaiveCatBoostResourcesModelMetadataReader(metadataHolder: Class<*>,
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