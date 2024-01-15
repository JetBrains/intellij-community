// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

fun createBaseCompletionMetrics(showByDefault: Boolean): List<Metric> =
  listOf(
    RecallAtMetric(showByDefault = showByDefault, n = 1),
    RecallAtMetric(showByDefault = showByDefault, n = 5),
    RecallMetric(),
    Precision(),
    MeanRankMetric(),
    MeanLatencyMetric(),
    SuccessMeanLatencyMetric(),
    MaxLatencyMetric(),
    PercentileLatencyMetric(percentile = 50),
    PercentileLatencyMetric(percentile = 80),
    SuccessPercentileLatencyMetric(percentile = 50),
    SuccessPercentileLatencyMetric(percentile = 80),
    SuggestionsCountMetric(),
  )


fun createCompletionGolfMetrics(): List<Metric> =
  listOf(
    MatchedRatio(),
    MatchedRatioAt(showByDefault = false, n = 1),
    MatchedRatioAt(showByDefault = false, n = 3),
    PrefixSimilarity(),
    EditSimilarity(),
    SelectedAtMetric(false, n = 1),
    SelectedAtMetric(false, n = 5),
    MovesCount(),
    TypingsCount(),
    NavigationsCount(),
    CompletionInvocationsCount(),
    MovesCountNormalised(),
    PerfectLine(showByDefault = false),
  )

fun createBenchmarkMetrics(): List<Metric> =
  listOf(
    MatchedRatio(showByDefault = true),
    MatchedRatioAt(showByDefault = false, n = 1),
    MatchedRatioAt(showByDefault = false, n = 3),
    PrefixSimilarity(showByDefault = false),
    EditSimilarity(showByDefault = false),
    PerfectLine(showByDefault = true),
    SelectedAtMetric(false, n = 1),
    SelectedAtMetric(false, n = 5),
    CancelledMetric(showByDefault = false),
    CancelledAtMetric(showByDefault = false, n = 1),
    CancelledAtMetric(showByDefault = false, n = 5),
    MatchedLineLength(),
    FirstPerfectLineSession(),
  )

internal abstract class CompletionGolfMetric<T : Number> : Metric {
  protected var sample = Sample()

  private fun T.alsoAddToSample(): T = also { sample.add(it.toDouble()) }

  override fun evaluate(sessions: List<Session>): T = compute(sessions).alsoAddToSample()

  abstract fun compute(sessions: List<Session>): T
}

internal class MovesCount : CompletionGolfMetric<Int>() {
  override val name = NAME
  override val description: String = "Number of actions to write the file using completion"
  override val showByDefault: Boolean = true
  override val valueType = MetricValueType.INT
  override val value: Double
    get() = sample.sum()

  private val metrics = listOf(
    TypingsCount(),
    NavigationsCount(),
    CompletionInvocationsCount()
  )

  override fun compute(sessions: List<Session>): Int {
    // Add x2 amount of lookups, assuming that before each action we call code completion
    // We summarize 3 types of actions:
    // call code completion (1 point)
    // choice suggestion from completion or symbol (if there is no offer in completion) (1 point)
    // navigation to the suggestion (if it fits) (N points, based on suggestion index, assuming first index is 0)
    return metrics.sumOf { it.compute(sessions) }
  }

  companion object {
    const val NAME = "Total Moves"
  }
}

internal class TypingsCount : CompletionGolfMetric<Int>() {
  override val name = "Typings"
  override val description: String = "Number of typing new symbols"
  override val valueType = MetricValueType.INT
  override val showByDefault = false
  override val value: Double
    get() = sample.sum()

  override fun compute(sessions: List<Session>): Int =
    sessions.sumOf {
      it.expectedLength() +
      it.lookups.count { it.selectedPosition >= 0 } -
      it.lookups.sumOf { it.selectedWithoutPrefix()?.length ?: 0 }
    }
}

internal class NavigationsCount : CompletionGolfMetric<Int>() {
  override val name = "Navigations"
  override val description: String = "Number of navigations between proposals in code completion results"
  override val valueType = MetricValueType.INT
  override val showByDefault = false
  override val value: Double
    get() = sample.sum()

  override fun compute(sessions: List<Session>): Int = sessions.sumOf { computeMoves(it) }

  private fun computeMoves(session: Session) = session.lookups.sumOf { if (it.selectedPosition >= 0) it.selectedPosition else 0 }
}


internal class CompletionInvocationsCount : CompletionGolfMetric<Int>() {
  override val name = "Completion Invocations"
  override val description: String = "Number of code completion invocations"
  override val valueType = MetricValueType.INT
  override val showByDefault = false
  override val value: Double
    get() = sample.sum()

  override fun compute(sessions: List<Session>): Int =
    sessions.sumOf { it.lookups.count { lookup -> lookup.isNew } }
}

internal class MatchedLineLength : Metric {
  private val sample = mutableListOf<Double>()
  override val name: String = "Matched Line Length"
  override val description: String = "Maximum length of selected proposal in line (avg by lines)"
  override val valueType: MetricValueType = MetricValueType.DOUBLE
  override val showByDefault: Boolean = false

  override val value: Double
    get() = sample.average()

  override fun evaluate(sessions: List<Session>): Double {
    val fileSample = Sample()
    for (session in sessions) {
      val value = session.lookups.maxOfOrNull { lookup -> lookup.selectedWithoutPrefix()?.length ?: 0 }?.toDouble() ?: 0.0
      fileSample.add(value)
      sample.add(value)
    }
    return fileSample.mean()
  }
}

internal class FirstPerfectLineSession : Metric {
  private val sample = mutableListOf<Double>()
  override val name: String = "First Perfect Line Session"
  override val description: String = "Index of first lookup with proposal matches until the end of line (avg by lines)"
  override val valueType: MetricValueType = MetricValueType.DOUBLE
  override val showByDefault: Boolean = false

  override val value: Double
    get() = sample.average()

  override fun evaluate(sessions: List<Session>): Number {
    val fileSample = Sample()
    for (session in sessions) {
      val value = session.lookups.indexOfFirst { lookup -> (lookup.selectedWithoutPrefix()?.length ?: 0) +
        lookup.offset == session.expectedText.length }.takeIf { it != -1 } ?: session.lookups.size
      fileSample.add(value)
      sample.add(value.toDouble())
    }
    return fileSample.mean()
  }
}

internal class MovesCountNormalised : Metric {
  private var movesCountTotal: Int = 0
  private var minPossibleMovesTotal: Int = 0
  private var maxPossibleMovesTotal: Int = 0

  override val name = "Moves Count Normalised"
  override val description: String = "Number of actions to write the file using completion normalized by minimum possible count"
  override val showByDefault: Boolean = true
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = (movesCountTotal - minPossibleMovesTotal).toDouble() / (maxPossibleMovesTotal - minPossibleMovesTotal)

  override fun evaluate(sessions: List<Session>): Double {
    val movesCount = MovesCount().compute(sessions)
    val minPossibleMoves = sessions.count() * 2
    val maxPossibleMoves = sessions.sumOf { it.expectedLength() + it.completableLength }
    movesCountTotal += movesCount
    minPossibleMovesTotal += minPossibleMoves
    maxPossibleMovesTotal += maxPossibleMoves

    // Since code completion's call and the choice of option (symbol) contains in each lookup,
    // It is enough to calculate the difference for the number of lookups and extra moves (for completion case)
    // To reach 0%, you also need to subtract the minimum number of lookups (eq. number of sessions plus minimum amount of completion calls)
    // 0% - best scenario, every line was completed from start to end with first suggestion in list
    // >100% is possible, when navigation in completion takes too many moves
    if (maxPossibleMoves == minPossibleMoves) {
      return 0.0
    }
    return ((movesCount - minPossibleMoves).toDouble() / (maxPossibleMoves - minPossibleMoves))
  }
}

internal class PerfectLine(showByDefault: Boolean) : SimilarityMetric(showByDefault) {
  override val name = "Perfect Line"
  override val description: String = "Ratio of completions with proposal matches until the end of line"

  override fun computeSimilarity(lookup: Lookup, expectedText: String): Double =
    if (lookup.selectedWithoutPrefix()?.length == expectedText.length) 1.0 else 0.0

  override fun computeExpected(lookup: Lookup, expectedText: String): Double = 1.0
}

private fun Session.expectedLength(): Int = expectedText.length - (lookups.firstOrNull()?.offset ?: 0)
