package com.intellij.completion.ml.ranker.local.catboost

import com.intellij.completion.ml.ranker.local.DecisionFunctionWithLanguages
import com.intellij.completion.ml.ranker.local.LocalZipModelProvider
import com.intellij.completion.ml.ranker.local.ZipCompletionRankingModelMetadataReader
import com.intellij.internal.ml.FeaturesInfo
import com.intellij.internal.ml.catboost.NaiveCatBoostModel
import com.intellij.internal.ml.completion.CompletionRankingModelBase
import com.intellij.openapi.diagnostic.logger
import java.util.zip.ZipFile

class LocalCatBoostModelProvider : LocalZipModelProvider {
  companion object {
    private const val MODEL_FILE = "model.bin"
    private val LOG = logger<LocalCatBoostModelProvider>()
  }
  override fun isSupportedFormat(file: ZipFile): Boolean {
    return file.getEntry(MODEL_FILE) != null
  }

  override fun loadModel(file: ZipFile): DecisionFunctionWithLanguages {
    val reader = ZipCompletionRankingModelMetadataReader(file)
    val metadata = FeaturesInfo.buildInfo(reader)
    val languages = reader.getSupportedLanguages()
    val stream = reader.tryGetResourceAsStream(MODEL_FILE) ?: throw IllegalStateException("Can't find '$MODEL_FILE' resource in zip file")
    val model = stream.use { NaiveCatBoostModel.loadModel(it) }

    return DecisionFunctionWithLanguages(object : CompletionRankingModelBase(metadata) {
      override fun predict(features: DoubleArray): Double {
        try {
          return model.makePredict(features)
        }
        catch (t: Throwable) {
          LOG.error(t)
          return 0.0
        }
      }
    }, languages)
  }
}