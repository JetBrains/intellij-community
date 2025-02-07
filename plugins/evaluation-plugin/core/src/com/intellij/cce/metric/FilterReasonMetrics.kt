package com.intellij.cce.metric

import com.google.gson.Gson
import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample
import com.intellij.openapi.diagnostic.thisLogger
import kotlin.math.max

fun SessionFilterReasonMetrics(sessions: List<Session>): List<Metric> = listOf(
  AllProposalsMetric(),
  RelevantProposalsMetric(true),
  RelevantProposalsMetric(false),
  FilteredByModelProposalsMetric("filter"),
  FilteredByModelProposalsMetric("trigger"),
  FetchedFromCacheProposalsMetric(true),
  FetchedFromCacheProposalsMetric(false),

  GroupingOfIdenticalSuggestionsMetric(),
  MaxSuggestionsForAnalysisLimitMetric(),
  MultipleFailureReasonsMetric(),
) + GenerateHardFilterMetrics(sessions)

fun GenerateHardFilterMetrics(sessions: List<Session>): List<Metric> {
  hardFilteredProposalMetrics.clear()

  hardFilteredProposalMetrics.addAll(sessions.flatMap { it.lookups }
                                       .flatMap { lookup ->
                                         val rawFiltered = lookup.additionalInfo["raw_filtered"] as? List<*> ?: emptyList<Any>()
                                         val analyzedFiltered = lookup.additionalInfo["analyzed_filtered"] as? List<*> ?: emptyList<Any>()
                                         rawFiltered + analyzedFiltered
                                       }
                                       .map { it as? Map<String, List<String>> ?: emptyMap() }
                                       .flatMap { map -> map["second"] as? List<String> ?: emptyList() }
                                       .distinct()
                                       .map { HardFilteredProposalsMetric(it) }
  )
  return hardFilteredProposalMetrics
}

private val hardFilteredProposalMetrics: MutableList<Metric> = mutableListOf()

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
             .map { it.additionalInfo["raw_proposals"] as? List<*> ?: emptyList<Any>() }
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
      .filter { it.isRelevant == isRelevant }
      .size
  }
}

class FilteredByModelProposalsMetric(val modelType: String) : SessionFilterReasonMetric("filtered by model") {
  override val name: String = super.name.removeSuffix("model") + "${modelType}model"
  override val description: String = super.description.removeSuffix("model") + "$modelType model"

  override fun compute(lookups: List<Lookup>): Int {
    return lookups
      .filter { it.additionalInfo["${modelType}_decision"] == "SKIP" }
      .size
  }
}

class HardFilteredProposalsMetric(val hardFilterDebugDescription: String) : SessionFilterReasonMetric("hard filtered") {
  override val name: String = super.name + hardFilterDebugDescription.filter { !it.isWhitespace() }
  override val description: String = hardFilterDebugDescription

  override fun compute(lookups: List<Lookup>): Int {
    return lookups
      .flatMap { lookup ->
        val rawFiltered = lookup.additionalInfo["raw_filtered"] as? List<*> ?: emptyList<Any>()
        val analyzedFiltered = lookup.additionalInfo["analyzed_filtered"] as? List<*> ?: emptyList<Any>()
        rawFiltered + analyzedFiltered
      }
      .map { it as? Map<String, List<String>> ?: emptyMap() }
      .flatMap { map -> map["second"] as? List<String> ?: emptyList() }
      .filter { it == hardFilterDebugDescription }
      .size
  }
}

class FetchedFromCacheProposalsMetric(val isRelevant: Boolean) : SessionFilterReasonMetric("fetched from cache") {
  private val correctness = if (isRelevant) "correct" else "incorrect"
  override val name: String = super.name + correctness
  override val description: String = super.description + " " + correctness

  override fun compute(lookups: List<Lookup>): Int {
    return lookups
      .filter { lookup ->
        val rawProposals = lookup.additionalInfo["raw_proposals"] as? List<*> ?: emptyList<Any>()
        rawProposals.isEmpty() && lookup.suggestions.isNotEmpty()
      }
      .flatMap { it.suggestions }
      .filter { it.isRelevant == isRelevant }
      .size
  }
}

class GroupingOfIdenticalSuggestionsMetric : SessionFilterReasonMetric("grouping of identical suggestions") {
  override fun compute(lookups: List<Lookup>): Int {
    return lookups.sumOf { lookup ->
      val rawProposalsSize = (lookup.additionalInfo["raw_proposals"] as? List<*>)?.size ?: 0
      val rawFilteredSize = (lookup.additionalInfo["raw_filtered"] as? List<*>)?.size ?: 0
      rawProposalsSize - rawFilteredSize
    }
  }
}

class MaxSuggestionsForAnalysisLimitMetric : SessionFilterReasonMetric("maxSuggestionsForAnalysis limit") {
  override fun compute(lookups: List<Lookup>): Int {
    return lookups.sumOf { lookup ->
      val rawFilteredList = lookup.additionalInfo["raw_filtered"] as? List<*> ?: emptyList<Any>()
      val analyzedFilteredSize = (lookup.additionalInfo["analyzed_filtered"] as? List<*>)?.size ?: 0

      val countZero = rawFilteredList.map { element ->
        val mapItem = element as? Map<String, List<String>> ?: emptyMap()
        (mapItem["second"] as? List<String>)?.size ?: 0
      }.count { it == 0 }

      countZero - analyzedFilteredSize
    }
  }
}

class MultipleFailureReasonsMetric : SessionFilterReasonMetric("multiple failure reasons") {
  override fun compute(lookups: List<Lookup>): Int {
    return lookups.sumOf { lookup ->
      val rawFilteredList = lookup.additionalInfo["raw_filtered"] as? List<*> ?: emptyList<Any>()
      rawFilteredList
        .map { element ->
          val mapItem = element as? Map<String, List<String>> ?: emptyMap()
          (mapItem["second"] as? List<String>)?.size ?: 0
        }
        .sumOf { size -> max(0, size - 1) }
    }
  }
}

private data class Branch(val head: String, val lambda: String? = null, val underFlow: String? = null, val children: List<Any> = emptyList())

fun generateJsonStructureForSankeyChart(): String {
  val gson = Gson()

  val correctSuggestionsBranch = Branch("correct suggestions",
                                        lambda = RelevantProposalsMetric(true).name,
                                        underFlow = "not fetched from cache",
                                        children = listOf(FetchedFromCacheProposalsMetric(true).name))

  val incorrectSuggestionsBranch = Branch("incorrect suggestions",
                                          lambda = RelevantProposalsMetric(false).name,
                                          underFlow = "not fetched from cache",
                                          children = listOf(FetchedFromCacheProposalsMetric(false).name))

  val suggestionsBranch = Branch("Suggestions",
                                 children = listOf(correctSuggestionsBranch, incorrectSuggestionsBranch))

  val hardFiltersBranch = Branch("Hard Filters",
                                 children = hardFilteredProposalMetrics.map { it.name })

  val multipleFailureReasonsBranch = Branch("Multiple Failure Reasons",
                                            children = listOf(MultipleFailureReasonsMetric().name))

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
                                    hardFiltersBranch,
                                    multipleFailureReasonsBranch,
                                    modelFilterBranch,
                                    preProcessingBranch))

  return gson.toJson(allProposalsBranch)
}