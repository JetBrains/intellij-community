package com.intellij.completion.ml.ranker.local.randomforest

import com.intellij.completion.ml.ranker.local.DecisionFunctionWithLanguages
import com.intellij.completion.ml.ranker.local.LocalZipModelProvider
import com.intellij.completion.ml.ranker.local.ZipCompletionRankingModelMetadataReader
import com.intellij.internal.ml.FeaturesInfo
import com.intellij.internal.ml.models.local.LocalRandomForestModel
import java.util.zip.ZipFile

class LocalRandomForestProvider : LocalZipModelProvider {
  companion object {
    private const val MODEL_FILE = "model.txt"
  }

  override fun isSupportedFormat(file: ZipFile): Boolean {
    return file.entries().asSequence().any { it.name.endsWith(MODEL_FILE) }
  }

  override fun loadModel(file: ZipFile): DecisionFunctionWithLanguages {
    val reader = ZipCompletionRankingModelMetadataReader(file)
    val modelText = reader.resourceContent(MODEL_FILE)
    val metadata = FeaturesInfo.buildInfo(reader)
    val decisionFunction = LocalRandomForestModel.loadModel(modelText, metadata)
    val languages = reader.getSupportedLanguages()
    return DecisionFunctionWithLanguages(decisionFunction, languages)
  }
}