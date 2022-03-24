// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.completion.ml.sorting

import com.intellij.codeInsight.completion.CompletionFinalSorter
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.util.prefix
import com.intellij.completion.ml.util.queryLength
import com.intellij.completion.ml.util.RelevanceUtil
import com.intellij.textMatching.PrefixMatchingUtil
import com.intellij.completion.ml.features.RankingFeaturesOverrides
import com.intellij.completion.ml.performance.CompletionPerformanceTracker
import com.intellij.completion.ml.settings.CompletionMLRankingSettings
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.diagnostic.logger
import com.intellij.completion.ml.personalization.session.SessionFactorsUtils
import com.intellij.completion.ml.storage.MutableLookupStorage
import com.intellij.internal.ml.completion.DecoratingItemsPolicy
import com.intellij.lang.Language
import java.util.*
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class MLSorterFactory : CompletionFinalSorter.Factory {
  override fun newSorter() = MLSorter()
}


class MLSorter : CompletionFinalSorter() {
  private companion object {
    private val LOG = logger<MLSorter>()
    private const val REORDER_ONLY_TOP_K = 5
  }

  private val cachedScore: MutableMap<LookupElement, ItemRankInfo> = IdentityHashMap()
  private val reorderOnlyTopItems: Boolean = Registry.`is`("completion.ml.reorder.only.top.items", true)

  override fun getRelevanceObjects(items: MutableIterable<LookupElement>): Map<LookupElement, List<Pair<String, Any>>> {
    if (cachedScore.isEmpty()) {
      return items.associate { it to listOf(Pair.create(FeatureUtils.ML_RANK, FeatureUtils.NONE as Any)) }
    }

    if (hasUnknownFeatures(items)) {
      return items.associate { it to listOf(Pair.create(FeatureUtils.ML_RANK, FeatureUtils.UNDEFINED as Any)) }
    }

    if (!isCacheValid(items)) {
      return items.associate { it to listOf(Pair.create(FeatureUtils.ML_RANK, FeatureUtils.INVALID_CACHE as Any)) }
    }

    return items.associate {
      val result = mutableListOf<Pair<String, Any>>()
      val cached = cachedScore[it]
      if (cached != null) {
        result.add(Pair.create(FeatureUtils.ML_RANK, cached.mlRank))
        result.add(Pair.create(FeatureUtils.BEFORE_ORDER, cached.positionBefore))
      }
      it to result
    }
  }

  private fun isCacheValid(items: Iterable<LookupElement>): Boolean {
    return items.map { cachedScore[it]?.prefixLength }.toSet().size == 1
  }

  private fun hasUnknownFeatures(items: Iterable<LookupElement>) = items.any {
    val score = cachedScore[it]
    score?.mlRank == null
  }

  override fun sort(items: MutableIterable<LookupElement>, parameters: CompletionParameters): Iterable<LookupElement?> {
    val lookup = LookupManager.getActiveLookup(parameters.editor) as? LookupImpl ?: return items
    val lookupStorage = MutableLookupStorage.get(lookup) ?: return items
    // Do nothing if unable to reorder items or to log the weights
    if (!lookupStorage.shouldComputeFeatures()) return items
    val startedTimestamp = System.currentTimeMillis()
    val queryLength = lookup.queryLength()
    val prefix = lookup.prefix()

    val element2score = mutableMapOf<LookupElement, Double?>()
    val elements = items.toList()

    val positionsBefore = elements.withIndex().associate { it.value to it.index }

    tryFillFromCache(element2score, elements, queryLength)
    val itemsForScoring = if (element2score.size == elements.size) emptyList() else elements
    calculateScores(element2score, itemsForScoring, positionsBefore,
                    queryLength, prefix, lookup, lookupStorage, parameters)
    val finalRanking = sortByMlScores(elements, element2score, positionsBefore, lookupStorage, lookup)

    lookupStorage.performanceTracker.sortingPerformed(itemsForScoring.size, System.currentTimeMillis() - startedTimestamp)

    return finalRanking
  }

  private fun tryFillFromCache(element2score: MutableMap<LookupElement, Double?>,
                               items: List<LookupElement>,
                               queryLength: Int) {
    for ((position, element) in items.withIndex()) {
      val cachedInfo = getCachedRankInfo(element, queryLength, position)
      if (cachedInfo == null) return
      element2score[element] = cachedInfo.mlRank
    }
  }

  private fun calculateScores(element2score: MutableMap<LookupElement, Double?>,
                              items: List<LookupElement>,
                              positionsBefore: Map<LookupElement, Int>,
                              queryLength: Int,
                              prefix: String,
                              lookup: LookupImpl,
                              lookupStorage: MutableLookupStorage,
                              parameters: CompletionParameters) {
    if (items.isEmpty()) return

    val rankingModel = lookupStorage.model

    lookupStorage.initUserFactors(lookup.project)
    val meaningfulRelevanceExtractor = MeaningfulFeaturesExtractor()
    val relevanceObjects = lookup.getRelevanceObjects(items, false)
    val calculatedElementFeatures = mutableListOf<ElementFeatures>()
    for (element in items) {
      val position = positionsBefore.getValue(element)
      val (relevance, additional) = RelevanceUtil.asRelevanceMaps(relevanceObjects.getOrDefault(element, emptyList()))
      SessionFactorsUtils.saveElementFactorsTo(additional, lookupStorage, element)
      calculateAdditionalFeaturesTo(additional, element, queryLength, prefix.length, position, items.size, parameters)
      lookupStorage.performanceTracker.trackElementFeaturesCalculation(PrefixMatchingUtil.baseName) {
        PrefixMatchingUtil.calculateFeatures(element.lookupString, prefix, additional)
      }
      meaningfulRelevanceExtractor.processFeatures(relevance)
      calculatedElementFeatures.add(ElementFeatures(relevance, additional))
    }

    val lookupFeatures = mutableMapOf<String, Any>()
    for (elementFeatureProvider in LookupFeatureProvider.forLanguage(lookupStorage.language)) {
      val features = elementFeatureProvider.calculateFeatures(calculatedElementFeatures)
      lookupFeatures.putAll(features)
    }
    val additionalContextFeatures = mutableMapOf<String, String>()
    for (contextFeatureProvider in AdditionalContextFeatureProvider.forLanguage(lookupStorage.language)) {
      val features = contextFeatureProvider.calculateFeatures(lookupStorage.contextFactors)
      additionalContextFeatures.putAll(features)
    }

    val contextFactors = lookupStorage.contextFactors + additionalContextFeatures
    val commonSessionFactors = SessionFactorsUtils.updateSessionFactors(lookupStorage, items)
    val meaningfulRelevance = meaningfulRelevanceExtractor.meaningfulFeatures()
    val features = RankingFeatures(lookupStorage.userFactors, contextFactors, commonSessionFactors, lookupFeatures, meaningfulRelevance)

    val tracker = ModelTimeTracker()
    for ((i, element) in items.withIndex()) {
      val (relevance, additional) = overrideElementFeaturesIfNeeded(calculatedElementFeatures[i], lookupStorage.language)

      val score = tracker.measure {
        val position = positionsBefore.getValue(element)
        val elementFeatures = features.withElementFeatures(relevance, additional)
        return@measure calculateElementScore(rankingModel, element, position, elementFeatures, queryLength)
      }
      element2score[element] = score

      additional.putAll(relevance)
      lookupStorage.fireElementScored(element, additional, score)
    }

    tracker.finished(lookupStorage.performanceTracker)
  }

  private fun overrideElementFeaturesIfNeeded(elementFeatures: ElementFeatures, language: Language): ElementFeatures {
    for (it in RankingFeaturesOverrides.forLanguage(language)) {
      val overrides = it.getMlElementFeaturesOverrides(elementFeatures.additional)
      elementFeatures.additional.putAll(overrides)
      if (overrides.isNotEmpty())
        LOG.debug("The next ML features was overridden: [${overrides.map { it.key }.joinToString()}]")

      val relevanceOverrides = it.getDefaultWeigherFeaturesOverrides(elementFeatures.relevance)
      elementFeatures.relevance.putAll(relevanceOverrides)
      if (relevanceOverrides.isNotEmpty())
        LOG.debug("The next default weigher features was overridden: [${relevanceOverrides.map { it.key }.joinToString()}]")
    }
    return elementFeatures
  }

  private fun sortByMlScores(items: List<LookupElement>,
                             element2score: Map<LookupElement, Double?>,
                             positionsBefore: Map<LookupElement, Int>,
                             lookupStorage: MutableLookupStorage,
                             lookup: LookupImpl): Iterable<LookupElement> {
    val shouldSort = element2score.values.none { it == null } && lookupStorage.shouldReRank()
    if (LOG.isDebugEnabled) {
      LOG.debug("ML sorting in completion used=$shouldSort for language=${lookupStorage.language.id}")
    }

    if (shouldSort) {
      lookupStorage.fireReorderedUsingMLScores()
      val decoratingItemsPolicy = lookupStorage.model?.decoratingPolicy() ?: DecoratingItemsPolicy.DISABLED
      val topItemsCount = if (reorderOnlyTopItems) REORDER_ONLY_TOP_K else Int.MAX_VALUE
      return items
        .reorderByMLScores(element2score, topItemsCount)
        .markRelevantItemsIfNeeded(element2score, lookup, decoratingItemsPolicy)
        .addDiagnosticsIfNeeded(positionsBefore, topItemsCount, lookup)
    }

    return items
  }

  private fun calculateAdditionalFeaturesTo(
    additionalMap: MutableMap<String, Any>,
    lookupElement: LookupElement,
    oldQueryLength: Int,
    prefixLength: Int,
    position: Int,
    itemsCount: Int,
    parameters: CompletionParameters) {

    additionalMap["position"] = position
    additionalMap["relative_position"] = position.toDouble() / itemsCount
    additionalMap["query_length"] = oldQueryLength // old version of prefix_length feature
    additionalMap["prefix_length"] = prefixLength
    additionalMap["result_length"] = lookupElement.lookupString.length
    additionalMap["auto_popup"] = parameters.isAutoPopup
    additionalMap["completion_type"] = parameters.completionType.toString()
    additionalMap["invocation_count"] = parameters.invocationCount
  }

  private fun Iterable<LookupElement>.reorderByMLScores(element2score: Map<LookupElement, Double?>, toReorder: Int): Iterable<LookupElement> {
    val result = this
      .sortedByDescending { element2score.getValue(it) }
      .removeDuplicatesIfNeeded()
      .take(toReorder)
      .toCollection(linkedSetOf())
    result.addAll(this)
    return result
  }

  private fun Iterable<LookupElement>.removeDuplicatesIfNeeded(): Iterable<LookupElement> =
    if (Registry.`is`("completion.ml.reorder.without.duplicates", false)) this.distinctBy { it.lookupString } else this

  private fun Iterable<LookupElement>.addDiagnosticsIfNeeded(positionsBefore: Map<LookupElement, Int>, reordered: Int, lookup: LookupImpl): Iterable<LookupElement> {
    if (CompletionMLRankingSettings.getInstance().isShowDiffEnabled) {
      var positionChanged = false
      this.forEachIndexed { position, element ->
        val before = positionsBefore.getValue(element)
        if (before < reordered || position < reordered) {
          val diff = position - before
          positionChanged = positionChanged || diff != 0
          ItemsDecoratorInitializer.itemPositionChanged(element, diff)
        }
      }
      ItemsDecoratorInitializer.markAsReordered(lookup, positionChanged)
    }

    return this
  }

  private fun Iterable<LookupElement>.markRelevantItemsIfNeeded(element2score: Map<LookupElement, Double?>,
                                                                lookup: LookupImpl,
                                                                decoratingItemsPolicy: DecoratingItemsPolicy): Iterable<LookupElement> {
    if (CompletionMLRankingSettings.getInstance().isDecorateRelevantEnabled) {
      val relevantItems = decoratingItemsPolicy.itemsToDecorate(this.map { element2score[it] ?: 0.0 })
      for (index in relevantItems) {
        ItemsDecoratorInitializer.markAsRelevant(lookup, this.elementAt(index))
      }
    }
    return this
  }

  private fun getCachedRankInfo(element: LookupElement, prefixLength: Int, position: Int): ItemRankInfo? {
    val cached = cachedScore[element]
    if (cached != null && prefixLength == cached.prefixLength && cached.positionBefore == position) {
      return cached
    }
    return null
  }

  /**
   * Null means we encountered unknown features and are unable to score
   */
  private fun calculateElementScore(ranker: RankingModelWrapper?,
                                    element: LookupElement,
                                    position: Int,
                                    features: RankingFeatures,
                                    prefixLength: Int): Double? {
    val mlRank: Double? = if (ranker != null && ranker.canScore(features)) ranker.score(features) else null
    val info = ItemRankInfo(position, mlRank, prefixLength)
    cachedScore[element] = info

    return info.mlRank
  }

  /**
   * Extracts features that have different values
   */
  private class MeaningfulFeaturesExtractor {
    private val meaningful = mutableSetOf<String>()
    private val values = mutableMapOf<String, Any>()

    fun processFeatures(features: Map<String, Any>) {
      for (feature in features) {
        when (values[feature.key]) {
          null -> values[feature.key] = feature.value
          feature.value -> Unit
          else -> meaningful.add(feature.key)
        }
      }
    }

    fun meaningfulFeatures(): Set<String> = meaningful
  }

  /*
   * Measures time on getting predictions from the ML model
   */
  private class ModelTimeTracker {
    private var itemsScored: Int = 0
    private var timeSpent: Long = 0L
    fun measure(scoringFun: () -> Double?): Double? {
      val start = System.nanoTime()
      val result = scoringFun.invoke()
      if (result != null) {
        itemsScored += 1
        timeSpent += System.nanoTime() - start
      }

      return result
    }

    fun finished(performanceTracker: CompletionPerformanceTracker) {
      if (itemsScored != 0) {
        performanceTracker.itemsScored(itemsScored, TimeUnit.NANOSECONDS.toMillis(timeSpent))
      }
    }
  }
}

private data class ItemRankInfo(val positionBefore: Int, val mlRank: Double?, val prefixLength: Int)
