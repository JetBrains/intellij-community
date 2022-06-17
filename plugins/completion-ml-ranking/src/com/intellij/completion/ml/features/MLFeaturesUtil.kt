// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.features

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.util.Version

internal object MLFeaturesUtil {
  fun getRawValue(featureValue: MLFeatureValue): Any {
    return when (featureValue) {
      is MLFeatureValue.BinaryValue -> if (featureValue.value) 1 else 0
      is MLFeatureValue.FloatValue -> featureValue.value
      is MLFeatureValue.CategoricalValue -> featureValue.value
      is MLFeatureValue.ClassNameValue -> getClassNameSafe(featureValue)
      is MLFeatureValue.VersionValue -> getVersionSafe(featureValue)
    }
  }

  private data class ClassNames(val simpleName: String, val fullName: String)

  private fun Class<*>.getNames(): ClassNames {
    return ClassNames(simpleName, name)
  }

  private val THIRD_PARTY_NAME = ClassNames("third.party", "third.party")

  private val CLASS_NAMES_CACHE = Caffeine.newBuilder().maximumSize(100).build<String, ClassNames>()

  fun getClassNameSafe(feature: MLFeatureValue.ClassNameValue): String {
    val clazz = feature.value
    val names = CLASS_NAMES_CACHE.get(clazz.name) { if (getPluginInfo(clazz).isSafeToReport()) clazz.getNames() else THIRD_PARTY_NAME }!!
    return if (feature.useSimpleName) names.simpleName else names.fullName
  }

  private const val INVALID_VERSION = "invalid.version"

  fun getVersionSafe(featureValue: MLFeatureValue.VersionValue): String =
    Version.parseVersion(featureValue.value)?.toString() ?: INVALID_VERSION
}
