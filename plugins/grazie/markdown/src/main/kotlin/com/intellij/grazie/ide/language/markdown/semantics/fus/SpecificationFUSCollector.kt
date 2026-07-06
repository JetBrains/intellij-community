package com.intellij.grazie.ide.language.markdown.semantics.fus

import ai.grazie.rules.promptAnalysis.LlmAnalyzer.LlmIssue
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.Specification
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.WithSpending
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import kotlin.math.roundToLong

internal object SpecificationFUSCollector: CounterUsagesCollector() {
  private val GROUP = EventLogGroup("grazie.semantics", 5)

  override fun getGroup(): EventLogGroup = GROUP

  private val ANALYZER_FIELD = EventFields.String(
    "analyzer",
    listOf("CognitiveLoadAnalyzer", "AmbiguityAnalyzer", "ContradictionAnalyzer", "SecurityAnalyzer", "SemanticCoverageAnalyzer")
  )
  private val TIME_FIELD = EventFields.Long("timeMs")
  private val ISSUES_FIELD = EventFields.Int("issues")
  private val COST_FIELD = EventFields.RoundedLong("cost")
  private val TEXT_LENGTH_FIELD = EventFields.RoundedInt("textLength")
  private val FILE_COUNT_FIELD = EventFields.RoundedInt("fileCount")
  private val INDEX_FIELD = EventFields.Int("index")
  private val TOTAL_FIELD = EventFields.Int("total")

  private const val COEFFICIENT = 1_000_000L


  fun suggestionAccepted(index: Int, total: Int) = acceptSuggestionEvent.log(
    INDEX_FIELD.with(index),
    TOTAL_FIELD.with(total)
  )

  fun suggestionShown(index: Int, total: Int) = suggestionShownEvent.log(
    INDEX_FIELD.with(index),
    TOTAL_FIELD.with(total)
  )

  fun <T> analysisCompleted(
    analyzer: String, specifications: Set<Specification<T>>, analysis: WithSpending<Map<String, List<LlmIssue<T>>>>, timeMs: Long,
  ) {
    val textLength = specifications.sumOf { it.currentText.length }
    val issues = analysis.data.values.sumOf { it.size }
    val costs = (COEFFICIENT * analysis.spentCredits).roundToLong()
    analysisEvent.log(
      ANALYZER_FIELD.with(analyzer),
      TEXT_LENGTH_FIELD.with(textLength),
      FILE_COUNT_FIELD.with(specifications.size),
      COST_FIELD.with(costs),
      TIME_FIELD.with(timeMs),
      ISSUES_FIELD.with(issues),
    )
  }


  private val analysisEvent = GROUP.registerVarargEvent(
    "specification.analysis",
    ANALYZER_FIELD,
    TEXT_LENGTH_FIELD,
    FILE_COUNT_FIELD,
    COST_FIELD,
    TIME_FIELD,
    ISSUES_FIELD,
  )

  private val acceptSuggestionEvent = GROUP.registerVarargEvent(
    "suggestion.accepted",
    INDEX_FIELD,
    TOTAL_FIELD,
  )

  private val suggestionShownEvent = GROUP.registerVarargEvent(
    "suggestion.shown",
    INDEX_FIELD,
    TOTAL_FIELD,
  )
}
