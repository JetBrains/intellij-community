// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.ranker.local

import com.intellij.internal.ml.DecisionFunction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.SequentialTaskExecutor
import java.util.concurrent.Future
import java.util.zip.ZipFile

object MLCompletionLocalModelsUtil {
  private const val REGISTRY_PATH_KEY = "completion.ml.path.to.zip.model"
  private val LOG = Logger.getInstance(MLCompletionLocalModelsUtil::class.java)
  private val executor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("MLCompletionTxtModelsUtil pool executor")
  @Volatile private var localModel: LocalModalInfo? = null

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
    return if (languageId.toLowerCase() in resLocalModel.languages) {
      resLocalModel.decisionFunction
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
    localModel = loadModel(pathToZip)
    val endTime = System.currentTimeMillis()
    LOG.info("ML Completion local model initialization took: ${endTime - startTime} ms.")
  }

  private fun loadModel(pathToZip: String): LocalModalInfo? {
    try {
      ZipFile(pathToZip).use { file ->
        val loader = LocalZipModelProvider.findModelProvider(file) ?: return null
        val (decisionFunction, languages) = loader.loadModel(file)
        return LocalModalInfo(decisionFunction, pathToZip, languages.toSet())
      }
    } catch (t: Throwable) {
      LOG.error(t)
      return null
    }
  }

  private data class LocalModalInfo(val decisionFunction: DecisionFunction, val path: String, val languages: Set<String>)
}
