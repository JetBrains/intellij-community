package com.intellij.cce.metric

import com.google.gson.Gson
import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample
import com.intellij.openapi.diagnostic.thisLogger

fun createFilterReasonMetrics(sessions: List<Session>): List<Metric> = listOf(
  AllProposalsMetric(),
  RelevantProposalsMetric(true),
  RelevantProposalsMetric(false),
  FilteredByModelProposalsMetric("filter"),
  FilteredByModelProposalsMetric("trigger"),
  FetchedFromCacheProposalsMetric(true),
  FetchedFromCacheProposalsMetric(false),

  GroupingOfIdenticalSuggestionsMetric(),
  MaxSuggestionsForAnalysisLimitMetric(),
) + generateFilterMetrics(sessions)

private fun generateFilterMetrics(sessions: List<Session>): List<Metric> =
  sessions.flatMap { it.lookups }
    .flatMap { lookup -> lookup.rawFilteredDebugMessagesList + lookup.analyzedFilteredDebugMessagesList }
    .flatten()
    .distinct()
    .map { FilteredProposalsMetric(it) }


abstract class SessionFilterReasonMetric(debugDescription: String) : Metric {
  private val sample = Sample()
  override val name: String = "FilterReason${debugDescription.filterNot { it.isWhitespace() }}"
  override val description: String = debugDescription
  override val showByDefault: Boolean = false
  override val valueType: MetricValueType = MetricValueType.INT
  override val value: Double
    get() = sample.sum()

  abstract fun compute(lookups: List<Lookup>): Int

  override fun evaluate(sessions: List<Session>): Double {
    val lookups = sessions.flatMap { it.lookups }
    val suggestions = compute(lookups)

    sample.add(suggestions.toDouble())
    return suggestions.toDouble()
  }

  companion object {
    val LOG = thisLogger()
  }
}

class AllProposalsMetric : SessionFilterReasonMetric("all proposals") {
  override fun compute(lookups: List<Lookup>): Int {
    return lookups
             .map { it.rawProposalsList }
             .sumOf { it.size } +
           FetchedFromCacheProposalsMetric(true).compute(lookups) +
           FetchedFromCacheProposalsMetric(false).compute(lookups)
  }
}

class RelevantProposalsMetric(val isRelevant: Boolean) : SessionFilterReasonMetric("accepted proposals") {
  private val correctness = if (isRelevant) "correct" else "incorrect"
  override val name: String = super.name.removeSuffix("proposals") + "${correctness}proposals"
  override val description: String = super.description.removeSuffix("proposals") + " $correctness proposals"

  override fun compute(lookups: List<Lookup>): Int {
    return lookups
      .filter { it.suggestions.isNotEmpty() }
      .flatMap { it.suggestions }
      .count { it.isRelevant == isRelevant }
  }
}

class FilteredByModelProposalsMetric(val modelType: String) : SessionFilterReasonMetric("filtered by model") {
  override val name: String = super.name.removeSuffix("model") + "${modelType}model"
  override val description: String = super.description.removeSuffix("model") + "$modelType model"

  override fun compute(lookups: List<Lookup>): Int {
    return lookups
      .count { it.additionalInfo["${modelType}_decision"] == "SKIP" }
  }
}

class FilteredProposalsMetric(val filterDebugDescription: String) : SessionFilterReasonMetric(debugDescription) {
  override val name: String = super.name + filterDebugDescription.filter { !it.isWhitespace() }
  override val description: String = filterDebugDescription

  override fun compute(lookups: List<Lookup>): Int {
    return lookups
      .flatMap { it.rawFilteredDebugMessagesList + it.analyzedFilteredDebugMessagesList }
      .flatten()
      .count { it == filterDebugDescription }
  }

  companion object {
    const val debugDescription: String = "proposal filter"
  }
}

class FetchedFromCacheProposalsMetric(val isRelevant: Boolean) : SessionFilterReasonMetric("fetched from cache") {
  private val correctness = if (isRelevant) "correct" else "incorrect"
  override val name: String = super.name + correctness
  override val description: String = super.description + " " + correctness

  override fun compute(lookups: List<Lookup>): Int {
    return lookups
      .filter { it.rawFilteredList.isEmpty() && it.suggestions.isNotEmpty() }
      .flatMap { it.suggestions }
      .count { it.isRelevant == isRelevant }
  }
}

class GroupingOfIdenticalSuggestionsMetric : SessionFilterReasonMetric("grouping of identical suggestions") {
  override fun compute(lookups: List<Lookup>): Int {
    return lookups.sumOf { lookup ->
      lookup.rawProposalsList.size -
      lookup.rawFilteredList.size
    }
  }
}

class MaxSuggestionsForAnalysisLimitMetric : SessionFilterReasonMetric("maxSuggestionsForAnalysis limit") {
  override fun compute(lookups: List<Lookup>): Int {
    return lookups.sumOf { lookup ->
      lookup.rawFilteredDebugMessagesList.count { it.isEmpty() } -
      lookup.analyzedFilteredList.size
    }
  }
}

private val Lookup.rawProposalsList: List<String>
  get() = this.additionalInfo["raw_proposals"] as? List<String> ?: emptyList()

private val Lookup.rawFilteredList: List<String>
  get() = this.additionalInfo["raw_filtered"] as? List<String> ?: emptyList()

private val Lookup.analyzedFilteredList: List<String>
  get() = this.additionalInfo["analyzed_filtered"] as? List<String> ?: emptyList()

private val Lookup.rawFilteredDebugMessagesList: List<List<String>>
  get() = (this.additionalInfo["raw_filtered"] as? List<String> ?: emptyList<Any>()).map { element ->
    val mapItem = element as? Map<String, List<String>> ?: emptyMap()
    ((mapItem["second"] as? List<String>) ?: emptyList<String>()).take(1)
  }

private val Lookup.analyzedFilteredDebugMessagesList: List<List<String>>
  get() = (this.additionalInfo["analyzed_filtered"] as? List<*> ?: emptyList<Any>()).map { element ->
    val mapItem = element as? Map<String, List<String>> ?: emptyMap()
    ((mapItem["second"] as? List<String>) ?: emptyList<String>()).take(1)
  }

private data class Branch(val head: String, val lambda: String? = null, val underFlow: String? = null, val children: List<Any> = emptyList())

fun generateJsonStructureForSankeyChart(metrics: List<MetricInfo>): String {
  val gson = Gson()

  val correctSuggestionsBranch = Branch("correct suggestions",
                                        lambda = RelevantProposalsMetric(true).name,
                                        underFlow = "newly generated",
                                        children = listOf(FetchedFromCacheProposalsMetric(true).name))

  val incorrectSuggestionsBranch = Branch("incorrect suggestions",
                                          lambda = RelevantProposalsMetric(false).name,
                                          underFlow = "newly generated",
                                          children = listOf(FetchedFromCacheProposalsMetric(false).name))

  val suggestionsBranch = Branch("Suggestions",
                                 children = listOf(correctSuggestionsBranch, incorrectSuggestionsBranch))

  val wordFiltersBranch = Branch("Proposal Filter",
                                 children = metrics
                                   .filter { it.name.contains(FilteredProposalsMetric.debugDescription.filter { it.isLetterOrDigit() || it == '_' }) }
                                   .map { it.name })

  val modelFilterBranch = Branch("Model Filter",
                                 children = listOf(
                                   FilteredByModelProposalsMetric("filter").name,
                                   FilteredByModelProposalsMetric("trigger").name))

  val preProcessingBranch = Branch("Pre Processing",
                                   children = listOf(
                                     GroupingOfIdenticalSuggestionsMetric().name,
                                     MaxSuggestionsForAnalysisLimitMetric().name))

  val allProposalsBranch = Branch("All Proposals",
                                  lambda = AllProposalsMetric().name,
                                  underFlow = "unaccounted for proposals",
                                  children = listOf(
                                    suggestionsBranch,
                                    wordFiltersBranch,
                                    modelFilterBranch,
                                    preProcessingBranch))

  return gson.toJson(allProposalsBranch)
}