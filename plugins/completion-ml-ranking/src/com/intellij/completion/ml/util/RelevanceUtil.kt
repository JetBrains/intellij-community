package com.intellij.completion.ml.util

import com.intellij.codeInsight.completion.ml.MLWeigherUtil
import com.intellij.completion.ml.features.MLCompletionWeigher
import com.intellij.completion.ml.sorting.FeatureUtils
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil

object RelevanceUtil {
  private val LOG = logger<RelevanceUtil>()

  private const val NULL_TEXT = "null"
  private val IGNORED_FACTORS = setOf("kotlin.byNameAlphabetical",
                                      "scalaMethodCompletionWeigher",
                                      "unresolvedOnTop",
                                      "alphabetic",
                                      "TabNineLookupElementWeigher",
                                      "AixLookupElementWeigher",
                                      "CodotaCompletionWeigher",
                                      "CodotaCompletionWeigher_Kotlin",
                                      "EmcSuggestionsWeigher",
                                      "codotaPriorityWeigher",
                                      "com.zlabs.code.completion.ScaCompletionWeigher",
                                      "com.aliyun.odps.studio.intellij.compiler.codecompletion.OdpsqlCompletionWeigher")

  private val weighersClassesCache = HashMap<String, Boolean>()

  /*
  * First map contains only features affecting default elements ordering
  * */
  fun asRelevanceMaps(relevanceObjects: List<Pair<String, Any?>>): kotlin.Pair<MutableMap<String, Any>, MutableMap<String, Any>> {
    val relevanceMap = HashMap<String, Any>()
    val additionalMap = mutableMapOf<String, Any>()
    for (pair in relevanceObjects) {
      val name = pair.first.normalized()
      val value = pair.second
      if (name in IGNORED_FACTORS || value == null) continue
      when (name) {
        "proximity" -> relevanceMap.addProximityValues("prox", value)
        "kotlin.proximity" -> relevanceMap.addProximityValues("kt_prox", value)
        "swiftSymbolProximity" -> {
          relevanceMap[name] = value // while this feature is used in actual models
          relevanceMap.addProximityValues("swift_prox", value)
        }
        "kotlin.callableWeight" -> relevanceMap.addDataClassValues("kotlin.callableWeight", value.toString())
        "ml_weigh" -> additionalMap.addMlFeatures("ml", value)
        else -> if (acceptValue(value) || name == FeatureUtils.ML_RANK) relevanceMap[name] = value
      }
    }

    return kotlin.Pair(relevanceMap, additionalMap)
  }

  private fun acceptValue(value: Any): Boolean {
    return value is Number || value is Boolean || value.javaClass.isEnum || isJetBrainsClass(value.javaClass)
  }

  private fun isJetBrainsClass(aClass: Class<*>): Boolean {
    return weighersClassesCache.getOrPut(aClass.name) { getPluginInfo(aClass).type.isDevelopedByJetBrains() }
  }

  private fun String.normalized(): String {
    return substringBefore('@')
  }

  private fun MutableMap<String, Any>.addMlFeatures(prefix: String, comparable: Any) {
    if (comparable !is MLCompletionWeigher.DummyComparable) {
      LOG.error("Unexpected value type of `$prefix`: ${comparable.javaClass.simpleName}")
      return
    }

    for ((name, value) in comparable.mlFeatures) {
      this["${prefix}_$name"] = value
    }
  }

  private fun MutableMap<String, Any>.addProximityValues(prefix: String, proximity: Any) {
    val weights = MLWeigherUtil.extractWeightsOrNull(proximity)
    if (weights == null) {
      LOG.error("Unexpected comparable type for `$prefix` weigher: ${proximity.javaClass.simpleName}")
      return
    }

    for ((name, weight) in weights) {
      this["${prefix}_$name"] = weight
    }
  }

  private fun MutableMap<String, Any>.addDataClassValues(featureName: String, dataClassString: String) {
    if (StringUtil.countChars(dataClassString, '(') != 1) {
      this[featureName] = dataClassString
    }
    else {
      this.addProperties(featureName, dataClassString.substringAfter('(').substringBeforeLast(')').split(','))
    }
  }

  private fun MutableMap<String, Any>.addProperties(prefix: String, properties: List<String>) {
    properties.forEach {
      if (it.isNotBlank()) {
        val key = "${prefix}_${it.substringBefore('=').trim()}"
        val value = it.substringAfter('=').trim()
        if (value == NULL_TEXT)
          return@forEach
        this[key] = value
      }
    }
  }
}