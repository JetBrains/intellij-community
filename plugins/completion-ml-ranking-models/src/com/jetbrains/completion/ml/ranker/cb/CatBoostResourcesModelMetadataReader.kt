package com.jetbrains.completion.ml.ranker.cb

import ai.catboost.CatBoostModel
import com.intellij.internal.ml.InconsistentMetadataException
import com.intellij.internal.ml.ResourcesModelMetadataReader

class CatBoostResourcesModelMetadataReader(metadataHolder: Class<*>,
                                           featuresDirectory: String,
                                           private val modelDirectory: String) : ResourcesModelMetadataReader(metadataHolder, featuresDirectory) {

  fun loadModel(): CatBoostModel {
    val resource = "$modelDirectory/model.cbm"
    val fileStream = metadataHolder.classLoader.getResourceAsStream(resource)
                     ?: throw InconsistentMetadataException(
                       "Metadata file not found: $resource. Resources holder: ${metadataHolder.name}")
    return CatBoostModel.loadModel(fileStream)
  }
}