// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.ranker.local

import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeaturesInfo
import com.intellij.internal.ml.completion.CompletionRankingModelBase
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.SequentialTaskExecutor
import java.util.*
import java.util.concurrent.Future

object MLCompletionLocalModelsUtil {
  private const val REGISTRY_PATH_KEY = "completion.ml.path.to.zip.model"
  private val LOG = Logger.getInstance(MLCompletionLocalModelsUtil::class.java)
  private val executor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("MLCompletionTxtModelsUtil pool executor")
  @Volatile private var localModel: CompletionLocalRankingModel? = null

  fun getModel(languageId: String): DecisionFunction? {
    if (!isPathToTheModelSet()) {
      localModel = null
      return null
    }
    if (isPathToTheModelChanged()) {
      scheduleInitModel()
      return null
    }

    val resLocalModel = localModel ?: return null
    return if (languageId.toLowerCase() in resLocalModel.getSupportedLanguages()) {
      resLocalModel
    }
    else {
      null
    }
  }

  /**
   * Use this function for asynchronously loading model
   */
  private fun scheduleInitModel(): Future<*> = executor.submit { initModelFromPathToZipSynchronously() }

  private fun isPathToTheModelSet() = Registry.get(REGISTRY_PATH_KEY).isChangedFromDefault

  private fun isPathToTheModelChanged() = Registry.stringValue(REGISTRY_PATH_KEY) != localModel?.path

  private fun initModelFromPathToZipSynchronously() {
    localModel = null
    val startTime = System.currentTimeMillis()
    val pathToZip = Registry.stringValue(REGISTRY_PATH_KEY)
    val resourcesReader = ZipModelMetadataReader(pathToZip)
    localModel = resourcesReader.readModel()
    val endTime = System.currentTimeMillis()
    LOG.info("ML Completion local model initialization took: ${endTime - startTime} ms.")
  }

  private class Tree(val thresholds: List<Double>,
                     val values: List<Double>,
                     val features: List<Int>,
                     val left: List<Int>,
                     val right: List<Int>) {
    private fun traverse(node: Int, featuresValues: DoubleArray): Double {
      assert (node != -1)
      return if (left[node] != -1) {
        val featureId = features[node]
        val featureValue = featuresValues[featureId]
        val threshold = thresholds[node]
        if (featureValue <= threshold) {
          traverse(left[node], featuresValues)
        }
        else {
          traverse(right[node], featuresValues)
        }
      }
      else {
        values[node]
      }
    }

    fun predict(featuresValues: DoubleArray): Double {
      return traverse(0, featuresValues)
    }
  }

  class TreesModel {
    private val trees: ArrayList<Tree> = ArrayList()

    fun addTree(thresholds: List<Double>,
                values: List<Double>,
                features: List<Int>,
                left: List<Int>,
                right: List<Int>) {
      trees.add(Tree(thresholds, values, features, left, right))
    }

    fun predict(featuresValues: DoubleArray?): Double {
      if (featuresValues == null) return 0.0
      val koef = 1.0 / trees.size
      val sum = trees.stream()
        .mapToDouble { it.predict(featuresValues) }
        .sum()
      return sum * koef
    }
  }

  class CompletionLocalRankingModel(metadata: FeaturesInfo, val path: String, val treesModel: TreesModel, val languages: List<String>)
    : CompletionRankingModelBase(metadata) {
    override fun predict(features: DoubleArray?): Double = treesModel.predict(features)
    fun getSupportedLanguages(): List<String> = languages
  }
}
