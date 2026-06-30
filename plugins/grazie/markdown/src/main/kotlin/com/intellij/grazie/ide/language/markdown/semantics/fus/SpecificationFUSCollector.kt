package com.intellij.grazie.ide.language.markdown.semantics.fus

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import java.util.UUID

internal object SpecificationFUSCollector: CounterUsagesCollector() {
  private val GROUP = EventLogGroup("grazie.semantics", 1)

  override fun getGroup(): EventLogGroup = GROUP

  private val ID_FIELD = EventFields.StringValidatedByInlineRegexp(
    "id", "^[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}$"
  )
  private val ANALYZER_FIELD = EventFields.String(
    "analyzer",
    listOf("CognitiveLoadAnalyzer", "AmbiguityAnalyzer", "ContradictionAnalyzer", "SecurityAnalyzer", "SemanticCoverageAnalyzer")
  )
  private val TIME_FIELD = EventFields.Long("timeMs")
  private val ISSUES_FIELD = EventFields.Int("issues")
  private val COST_FIELD = EventFields.Double("cost")
  private val TEXT_LENGTH_FIELD = EventFields.Int("textLength")
  private val INDEX_FIELD = EventFields.Int("index")
  private val TOTAL_FIELD = EventFields.Int("total")


  fun suggestionAccepted(id: UUID, index: Int, total: Int) = acceptSuggestionEvent.log(
    ID_FIELD.with(id.toString()),
    INDEX_FIELD.with(index),
    TOTAL_FIELD.with(total)
  )

  fun suggestionShown(id: UUID, index: Int, total: Int) = suggestionShownEvent.log(
    ID_FIELD.with(id.toString()),
    INDEX_FIELD.with(index),
    TOTAL_FIELD.with(total)
  )

  fun analysisCompleted(id: UUID, analyzer: String, textLength: Int, cost: Double, time: Long, issues: Int) = analysisEvent.log(
    ID_FIELD.with(id.toString()),
    ANALYZER_FIELD.with(analyzer),
    TEXT_LENGTH_FIELD.with(textLength),
    COST_FIELD.with(cost),
    TIME_FIELD.with(time),
    ISSUES_FIELD.with(issues),
  )


  private val analysisEvent = GROUP.registerVarargEvent(
    "specification.analysis",
    ID_FIELD,
    ANALYZER_FIELD,
    TEXT_LENGTH_FIELD,
    COST_FIELD,
    TIME_FIELD,
    ISSUES_FIELD,
  )

  private val acceptSuggestionEvent = GROUP.registerVarargEvent(
    "suggestion.accepted",
    ID_FIELD,
    INDEX_FIELD,
    TOTAL_FIELD,
  )

  private val suggestionShownEvent = GROUP.registerVarargEvent(
    "suggestion.shown",
    ID_FIELD,
    INDEX_FIELD,
    TOTAL_FIELD,
  )
}
