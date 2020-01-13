// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.history

import com.intellij.filePrediction.FilePredictionFeature
import com.intellij.filePrediction.FilePredictionFeature.Companion.numerical
import com.intellij.filePrediction.FilePredictionFeatureProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class FilePredictionHistoryFeatures: FilePredictionFeatureProvider {
  override fun getName(): String = "history"

  override fun calculateFileFeatures(project: Project, newFile: VirtualFile, prevFile: VirtualFile?): Map<String, FilePredictionFeature> {
    val result = HashMap<String, FilePredictionFeature>()
    val history = FilePredictionHistory.getInstance(project)
    result["size"] = numerical(history.size())

    val (position, uniGram, biGram) = history.calcHistoryFeatures(newFile.url)
    result["position"] = numerical(position)
    addNGramFeatures(uniGram, "uni", result)
    addNGramFeatures(biGram, "bi", result)
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
