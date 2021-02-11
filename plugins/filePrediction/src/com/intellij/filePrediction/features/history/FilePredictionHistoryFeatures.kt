// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history

import com.intellij.filePrediction.features.FilePredictionFeature
import com.intellij.filePrediction.features.FilePredictionFeature.Companion.numerical
import com.intellij.filePrediction.features.FilePredictionFeatureProvider
import com.intellij.filePrediction.features.FilePredictionFeaturesCache
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class FilePredictionHistoryFeatures : FilePredictionFeatureProvider {
  companion object {
    private val FEATURES = arrayListOf(
      "bi_max",
      "bi_min",
      "bi_mle",
      "bi_mle_to_max",
      "bi_mle_to_min",
      "position",
      "size",
      "tri_max",
      "tri_min",
      "tri_mle",
      "tri_mle_to_max",
      "tri_mle_to_min",
      "uni_max",
      "uni_min",
      "uni_mle",
      "uni_mle_to_max",
      "uni_mle_to_min"
    )
  }

  override fun getName(): String = "history"

  override fun getFeatures(): List<String> = FEATURES

  override fun calculateFileFeatures(project: Project,
                                     newFile: VirtualFile,
                                     prevFile: VirtualFile?,
                                     cache: FilePredictionFeaturesCache): Map<String, FilePredictionFeature> {
    val result = HashMap<String, FilePredictionFeature>()
    val history = FilePredictionHistory.getInstance(project)
    result["size"] = numerical(history.size())

    val (position, uniGram, biGram) = history.calcHistoryFeatures(newFile.url)
    if (position != null) {
      result["position"] = numerical(position)
    }
    addNGramFeatures(uniGram, "uni", result)
    addNGramFeatures(biGram, "bi", result)

    cache.nGrams.calculateFileFeatures(newFile.url)?.let {
      addNGramFeatures(it, "tri", result)
    }
    return result
  }

  private fun addNGramFeatures(probability: NextFileProbability, prefix: String, result: HashMap<String, FilePredictionFeature>) {
    result[prefix + "_mle"] = numerical(probability.mle)
    result[prefix + "_min"] = numerical(probability.minMle)
    result[prefix + "_max"] = numerical(probability.maxMle)
    result[prefix + "_mle_to_min"] = numerical(probability.mleToMin)
    result[prefix + "_mle_to_max"] = numerical(probability.mleToMax)
  }
}
