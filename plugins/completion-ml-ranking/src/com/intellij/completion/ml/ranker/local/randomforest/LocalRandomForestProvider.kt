package com.intellij.completion.ml.ranker.local.randomforest

import com.intellij.completion.ml.ranker.local.DecisionFunctionWithLanguages
import com.intellij.completion.ml.ranker.local.LocalZipModelProvider
import com.intellij.completion.ml.ranker.local.ZipModelMetadataReader
import com.intellij.internal.ml.FeaturesInfo
import java.util.zip.ZipFile

class LocalRandomForestProvider : LocalZipModelProvider {
  companion object {
    private const val MODEL_FILE = "model.txt"
  }

  override fun isSupportedFormat(file: ZipFile): Boolean {
    return file.entries().asSequence().any { it.name.endsWith(MODEL_FILE) }
  }

  override fun loadModel(file: ZipFile): DecisionFunctionWithLanguages {
    val reader = ZipModelMetadataReader(file)
    val modelText = reader.resourceContent(MODEL_FILE)
    val metadata = FeaturesInfo.buildInfo(reader)
    val decisionFunction = LocalRandomForestModel.loadModel(modelText, metadata)
    val languages = reader.getSupportedLanguages()
    return DecisionFunctionWithLanguages(decisionFunction, languages)
  }
}