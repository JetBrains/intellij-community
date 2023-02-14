// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class FeaturesInfo(override val knownFeatures: Set<String>,
                   override val binaryFeatures: List<BinaryFeature>,
                   override val floatFeatures: List<FloatFeature>,
                   override val categoricalFeatures: List<CategoricalFeature>,
                   override val featuresOrder: Array<FeatureMapper>,
                   override val version: String?) : ModelMetadata {
  companion object {
    private val gson = Gson()
    private const val DEFAULT: String = "default"
    private const val USE_UNDEFINED: String = "use_undefined"

    private fun List<String>.withSafeWeighers(): Set<String> {
      val result = this.toMutableSet()
      result.add("prox_directoryType")
      result.add("kt_prox_directoryType")
      result.add("kotlin.unwantedElement")
      return result
    }

    fun buildInfo(reader: ModelMetadataReader): FeaturesInfo {
      val knownFeatures = reader.allKnown().fromJson<List<String>>().withSafeWeighers()

      val binaryFactors: List<BinaryFeature> = reader.binaryFeatures().fromJson<Map<String, Map<String, Any>>>()
        .map { binary(it.key, it.value) }
      val doubleFactors = reader.floatFeatures().fromJson<Map<String, Map<String, Any>>>()
        .map { float(it.key, it.value) }
      val categoricalFactors = reader.categoricalFeatures().fromJson<Map<String, List<String>>>()
        .map { categorical(it.key, it.value) }

      val order = reader.featureOrderDirect()

      val featuresIndex = buildFeaturesIndex(binaryFactors, doubleFactors, categoricalFactors)
      return FeaturesInfo(knownFeatures, binaryFactors, doubleFactors, categoricalFactors, buildMappers(featuresIndex, order),
                          reader.extractVersion())
    }

    fun binary(name: String, description: Map<String, Any>): BinaryFeature {
      val (first, second) = extractBinaryValuesMappings(description)
      val default = extractDefaultValue(name, description)
      return BinaryFeature(name, first, second, default, allowUndefined(description))
    }

    fun float(name: String, description: Map<String, Any>): FloatFeature {
      val default = extractDefaultValue(name, description)
      return FloatFeature(name, default, allowUndefined(description))
    }

    fun categorical(name: String, categories: List<String>): CategoricalFeature {
      return CategoricalFeature(name, categories.toSet())
    }

    private fun <T> String.fromJson(): T {
      val typeToken = object : TypeToken<T>() {}
      return gson.fromJson<T>(this, typeToken.type)
    }

    fun buildFeaturesIndex(vararg featureGroups: List<Feature>): Map<String, Feature> {
      fun <T : Feature> MutableMap<String, Feature>.addFeatures(features: List<T>): MutableMap<String, Feature> {
        for (feature in features) {
          if (feature.name in keys) throw InconsistentMetadataException(
            "Ambiguous feature description '${feature.name}': $feature and ${this[feature.name]}")
          this[feature.name] = feature
        }

        return this
      }
      val index = mutableMapOf<String, Feature>()
      for (features in featureGroups) {
        index.addFeatures(features)
      }
      return index
    }

    private fun allowUndefined(description: Map<String, Any>): Boolean {
      val value = description[USE_UNDEFINED]
      if (value is Boolean) {
        return value
      }

      return true
    }

    private fun extractDefaultValue(name: String, description: Map<String, Any>): Double {
      return description[DEFAULT].toString().toDoubleOrNull()
             ?: throw InconsistentMetadataException("Default value not found. Feature name: $name")
    }

    private fun extractBinaryValuesMappings(description: Map<String, Any>)
      : Pair<ValueMapping, ValueMapping> {
      val result = mutableListOf<ValueMapping>()
      for ((name, value) in description) {
        if (name == DEFAULT || name == USE_UNDEFINED) continue
        val mappedValue = value.toString().toDoubleOrNull()
        if (mappedValue == null) throw InconsistentMetadataException("Mapped value for binary feature should be double")
        result += name to mappedValue
      }

      assert(result.size == 2) { "Binary feature must contains 2 values, but found $result" }
      result.sortBy { it.first }
      return Pair(result[0], result[1])
    }

    fun buildMappers(features: Map<String, Feature>,
                             order: List<String>): Array<FeatureMapper> {
      val mappers = mutableListOf<FeatureMapper>()
      for (arrayFeatureName in order) {
        val name = arrayFeatureName.substringBefore('=')
        val suffix = arrayFeatureName.indexOf('=').let { if (it == -1) null else arrayFeatureName.substring(it + 1) }
        val mapper = features[name]?.createMapper(suffix) ?: throw InconsistentMetadataException(
          "Unexpected feature name in array: $arrayFeatureName")
        mappers.add(mapper)
      }

      return mappers.toTypedArray()
    }
  }
}