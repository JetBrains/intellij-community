package com.intellij.filePrediction.features

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase

internal interface FileFeaturesProducer {
  fun produce(project: Project): Map<String, FilePredictionFeature>
}

internal class ConstFileFeaturesProducer(vararg included: Pair<String, FilePredictionFeature>) : FileFeaturesProducer {
  val features: MutableMap<String, FilePredictionFeature> = hashMapOf()

  init {
    for (pair in included) {
      features[pair.first] = pair.second
    }
  }

  override fun produce(project: Project): Map<String, FilePredictionFeature> {
    return features
  }
}

internal class FileFeaturesByProjectPathProducer(vararg included: Pair<String, FilePredictionFeature>) : FileFeaturesProducer {
  val featuresToAdjust: Set<String> = hashSetOf("path_prefix")
  val features: Array<out Pair<String, FilePredictionFeature>> = included

  override fun produce(project: Project): Map<String, FilePredictionFeature> {
    val dir = project.guessProjectDir()?.path
    CodeInsightFixtureTestCase.assertNotNull(dir)

    val prefixLength = dir!!.length + 1

    val result: MutableMap<String, FilePredictionFeature> = hashMapOf()
    for (feature in features) {
      val value = feature.second.toString().toIntOrNull()
      if (featuresToAdjust.contains(feature.first) && value != null) {
        result[feature.first] = FilePredictionFeature.numerical(prefixLength + value)
      }
      else {
        result[feature.first] = feature.second
      }
    }
    return result
  }
}
