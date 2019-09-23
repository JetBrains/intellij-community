// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml

sealed class Feature {
  abstract val name: String
  abstract fun createMapper(suffix: String?): FeatureMapper

  companion object {
    const val UNDEFINED: String = "UNDEFINED"
  }
}

internal typealias ValueMapping = Pair<String, Double>

data class BinaryFeature(override val name: String,
                         val firstValueMapping: ValueMapping,
                         val secondValueMapping: ValueMapping,
                         val defaultValue: Double,
                         val allowUndefined: Boolean) : Feature() {

  override fun createMapper(suffix: String?): FeatureMapper {
    return when (suffix) {
      UNDEFINED -> UndefinedMapper.checkAndCreate(name, allowUndefined)
      null -> BinaryMapper()
      else -> throw InconsistentMetadataException("Unexpected binary feature suffix: $suffix (feature '$name')")
    }
  }

  private inner class BinaryMapper : FeatureMapper {
    override fun getFeatureName(): String = name

    override fun asArrayValue(value: Any?): Double {
      return when (value.toString()) {
        firstValueMapping.first -> firstValueMapping.second
        secondValueMapping.first -> secondValueMapping.second
        else -> defaultValue
      }
    }
  }
}

data class FloatFeature(override val name: String, val defaultValue: Double, val allowUndefined: Boolean) : Feature() {
  override fun createMapper(suffix: String?): FeatureMapper {
    return when (suffix) {
      UNDEFINED -> UndefinedMapper.checkAndCreate(name, allowUndefined)
      null -> FloatMapper()
      else -> throw InconsistentMetadataException("Unexpected float feature suffix: $suffix (feature '$name')")
    }
  }

  private inner class FloatMapper : FeatureMapper {
    override fun getFeatureName(): String = name

    override fun asArrayValue(value: Any?): Double {
      return value.asDouble() ?: defaultValue
    }

    private fun Any?.asDouble(): Double? {
      if (this is Number) return this.toDouble()
      return this.toString().toDoubleOrNull()
    }
  }
}


data class CategoricalFeature(override val name: String, val categories: Set<String>) : Feature() {
  override fun createMapper(suffix: String?): FeatureMapper {
    return when (suffix) {
      UNDEFINED -> UndefinedMapper.checkAndCreate(name, UNDEFINED in categories)
      OTHER -> otherCategoryMapper()
      null -> throw InconsistentMetadataException("Categorical feature usage must have suffix")
      else -> if (suffix in categories) CategoryMapper(suffix)
      else throw InconsistentMetadataException("Unknown category '$suffix' of categorical feature '$name'")
    }
  }

  private fun otherCategoryMapper(): FeatureMapper {
    if (OTHER !in categories) throw InconsistentMetadataException("Feature $name does not allow other category")
    return OtherCategoryMapper(name, categories.minus(listOf(UNDEFINED, OTHER)))
  }

  private class OtherCategoryMapper(private val name: String, private val knownCategories: Set<String>) : FeatureMapper {
    override fun getFeatureName(): String = name

    override fun asArrayValue(value: Any?): Double = if (value == null || value.toString() in knownCategories) 0.0 else 1.0
  }

  private inner class CategoryMapper(private val category: String) : FeatureMapper {
    override fun getFeatureName(): String = name

    override fun asArrayValue(value: Any?): Double {
      if (value == null) {
        return 0.0
      }

      return if (value.toString() == category) 1.0 else 0.0
    }
  }

  companion object {
    const val OTHER: String = "OTHER"
  }
}

private class UndefinedMapper private constructor(private val featureName: String) : FeatureMapper {
  override fun getFeatureName(): String = featureName

  override fun asArrayValue(value: Any?): Double = if (value == null) 1.0 else 0.0

  companion object {
    fun checkAndCreate(featureName: String, canUseUndefined: Boolean): FeatureMapper {
      if (!canUseUndefined) {
        throw InconsistentMetadataException("Feature '$featureName' does not handle undefined values")
      }

      return UndefinedMapper(featureName)
    }
  }
}