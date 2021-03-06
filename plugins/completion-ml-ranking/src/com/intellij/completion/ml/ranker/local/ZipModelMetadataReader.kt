// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.ranker.local

import com.intellij.internal.ml.FeaturesInfo
import com.intellij.internal.ml.ModelMetadataReader
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ZipModelMetadataReader(private val zipFilePath: String): ModelMetadataReader {

  override fun binaryFeatures(): String = resourceContent("binary.json")
  override fun floatFeatures(): String = resourceContent("float.json")
  override fun categoricalFeatures(): String = resourceContent("categorical.json")
  override fun allKnown(): String = resourceContent("all_features.json")
  override fun featureOrderDirect(): List<String> = resourceContent("features_order.txt").lines()

  fun readModel(): MLCompletionLocalModelsUtil.CompletionLocalRankingModel {
    val metadata = FeaturesInfo.buildInfo(this)
    val treesModel = readTreesModel()
    val languages = getSupportedLanguages()
    return MLCompletionLocalModelsUtil.CompletionLocalRankingModel(metadata, zipFilePath, treesModel, languages)
  }

  override fun extractVersion(): String? {
    return null
  }

  private fun readTreesModel(): MLCompletionLocalModelsUtil.TreesModel {
    val reader = modelAsText().reader().buffered()
    val treesModel = MLCompletionLocalModelsUtil.TreesModel()
    val numberOfTrees = reader.readLine().toInt()
    for (i in 0 until numberOfTrees) {
      val left = reader.readLine().split(" ").map { it.toInt() }
      val right = reader.readLine().split(" ").map { it.toInt() }
      val thresholds = reader.readLine().split(" ").map { it.toDouble() }
      val features = reader.readLine().split(" ").map { it.toInt() }
      val values = reader.readLine().split(" ").map { it.toDouble() }
      treesModel.addTree(thresholds, values, features, left, right)
    }
    return treesModel
  }

  private fun modelAsText(): String = resourceContent("model.txt")

  private fun getSupportedLanguages(): List<String> = resourceContent("languages.txt").lines()

  private fun resourceContent(fileName: String): String {
    val zipFile = ZipFile(zipFilePath)

    val entries: Enumeration<out ZipEntry?> = zipFile.entries()
    while (entries.hasMoreElements()) {
      val entry = entries.nextElement() ?: continue
      if (entry.name.endsWith(fileName)) {
        val stream = zipFile.getInputStream(entry)
        return stream.bufferedReader().use { it.readText() }
      }
    }
    throw IllegalStateException("Can't find necessary file in zip file")
  }
}
