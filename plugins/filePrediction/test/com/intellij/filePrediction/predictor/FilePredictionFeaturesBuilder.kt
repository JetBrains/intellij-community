package com.intellij.filePrediction.predictor

import com.intellij.filePrediction.features.FilePredictionFeature
import com.intellij.filePrediction.features.history.FileHistoryFeatures
import com.intellij.filePrediction.features.history.NextFileProbability

@Suppress("unused")
internal class FilePredictionFeaturesBuilder {
  private val features: HashMap<String, FilePredictionFeature> = hashMapOf()

  fun withCustomFeature(name: String, value: Any): FilePredictionFeaturesBuilder {
    features[name] = newFeature(value)
    return this
  }

  private fun newFeature(value: Any): FilePredictionFeature {
    return when (value) {
      is Boolean -> FilePredictionFeature.binary(value)
      is Double -> FilePredictionFeature.numerical(value)
      is Int -> FilePredictionFeature.numerical(value)
      is String -> FilePredictionFeature.fileType(value)
      else -> FilePredictionFeature.fileType(value.toString())
    }
  }

  fun withVcs(inChangeList: Boolean): FilePredictionFeaturesBuilder {
    features["vcs_in_changelist"] = FilePredictionFeature.binary(inChangeList)
    return this
  }

  fun withInRef(inRef: Boolean): FilePredictionFeaturesBuilder {
    features["in_ref"] = FilePredictionFeature.binary(inRef)
    return this
  }

  fun withPositionFeatures(sameModule: Boolean, sameDirectory: Boolean): FilePredictionFeaturesBuilder {
    features["same_module"] = FilePredictionFeature.binary(sameModule)
    features["same_dir"] = FilePredictionFeature.binary(sameDirectory)
    return this
  }

  fun withStructureFeatures(excluded: Boolean, inProject: Boolean, inLibrary: Boolean, inSource: Boolean): FilePredictionFeaturesBuilder {
    features["excluded"] = FilePredictionFeature.binary(excluded)
    features["in_project"] = FilePredictionFeature.binary(inProject)
    features["in_library"] = FilePredictionFeature.binary(inLibrary)
    features["in_source"] = FilePredictionFeature.binary(inSource)
    return this
  }

  fun withNameFeatures(pathPrefix: Int, namePrefix: Int, relativePathPrefix: Int): FilePredictionFeaturesBuilder {
    features["path_prefix"] = FilePredictionFeature.numerical(pathPrefix)
    features["name_prefix"] = FilePredictionFeature.numerical(namePrefix)
    features["relative_path_prefix"] = FilePredictionFeature.numerical(relativePathPrefix)
    return this
  }

  fun withFileType(type: String): FilePredictionFeaturesBuilder {
    features["file_type"] = FilePredictionFeature.fileType(type)
    return this
  }

  fun withPrevFileType(type: String): FilePredictionFeaturesBuilder {
    features["prev_file_type"] = FilePredictionFeature.fileType(type)
    return this
  }

  fun withHistorySize(size: Int): FilePredictionFeaturesBuilder {
    features["history_size"] = FilePredictionFeature.numerical(size)
    return this
  }

  fun withHistoryPosition(position: Int?): FilePredictionFeaturesBuilder {
    if (position != null) {
      features["history_position"] = FilePredictionFeature.numerical(position)
    }
    return this
  }

  fun withUniGram(uni: NextFileProbability): FilePredictionFeaturesBuilder {
    addNGramFeatures(uni, "uni", features)
    return this
  }

  fun withHistory(history: FileHistoryFeatures): FilePredictionFeaturesBuilder {
    withHistoryPosition(history.position)
    addNGramFeatures(history.uniGram, "uni", features)
    addNGramFeatures(history.biGram, "bi", features)
    return this
  }

  private fun addNGramFeatures(probability: NextFileProbability, prefix: String, result: HashMap<String, FilePredictionFeature>) {
    result["history_" + prefix + "_mle"] = FilePredictionFeature.numerical(probability.mle)
    result["history_" + prefix + "_min"] = FilePredictionFeature.numerical(probability.minMle)
    result["history_" + prefix + "_max"] = FilePredictionFeature.numerical(probability.maxMle)
    result["history_" + prefix + "_mle_to_min"] = FilePredictionFeature.numerical(probability.mleToMin)
    result["history_" + prefix + "_mle_to_max"] = FilePredictionFeature.numerical(probability.mleToMax)
  }

  fun build(): Map<String, FilePredictionFeature> {
    return features
  }
}