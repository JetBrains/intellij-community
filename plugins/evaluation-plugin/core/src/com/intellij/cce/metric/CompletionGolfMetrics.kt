package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

abstract class CompletionGolfMetric<T : Number> : Metric {
  protected var sample = Sample()

  private fun T.alsoAddToSample(): T = also { sample.add(it.toDouble()) }

  protected fun computeMoves(session: Session): Int = session.filteredLookups.sumOf { if (it.selectedPosition >= 0) it.selectedPosition else 0 }

  protected fun computeCompletionCalls(sessions: List<Session>): Int = sessions.sumOf { it.filteredLookups.count { lookup -> lookup.isNew } }

  override fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator): T = compute(sessions, comparator).alsoAddToSample()

  abstract fun compute(sessions: List<Session>, comparator: SuggestionsComparator): T
}

class CompletionGolfMovesSumMetric : CompletionGolfMetric<Int>() {
  override val name: String = "Code Golf Moves Count"

  override val valueType = MetricValueType.INT

  override val value: Double
    get() = sample.sum()

  override fun compute(sessions: List<Session>, comparator: SuggestionsComparator): Int {
    // Add x2 amount of lookups, assuming that before each action we call code completion
    // We summarize 3 types of actions:
    // call code completion (1 point)
    // choice suggestion from completion or symbol (if there is no offer in completion) (1 point)
    // navigation to the suggestion (if it fits) (N points, based on suggestion index, assuming first index is 0)
    return sessions.sumOf { computeMoves(it) + it.filteredLookups.count() }
      .plus(computeCompletionCalls(sessions))
  }
}

class CompletionGolfMovesCountNormalised : CompletionGolfMetric<Double>() {
  override val name: String = "Code Golf Moves Count Normalised"

  override val valueType = MetricValueType.DOUBLE

  override val value: Double
    get() = sample.mean()

  override fun compute(sessions: List<Session>, comparator: SuggestionsComparator): Double {
    val linesLength = sessions.sumOf { it.expectedText.length } * 2.0
    val amountOfMoves = sessions.sumOf { computeMoves(it) + it.filteredLookups.count() } + computeCompletionCalls(sessions)

    val subtrahend = sessions.count() * 2.0

    // Since code completion's call and the choice of option (symbol) contains in each lookup,
    // It is enough to calculate the difference for the number of lookups and extra moves (for completion case)
    // To reach 0%, you also need to subtract the minimum number of lookups (eq. number of sessions plus minimum amount of completion calls)
    // 0% - best scenario, every line was completed from start to end with first suggestion in list
    // >100% is possible, when navigation in completion takes too many moves
    return ((amountOfMoves - subtrahend) / (linesLength - subtrahend))
  }
}

class CompletionGolfPerfectLine : CompletionGolfMetric<Int>() {
  override val name: String = "Code Golf Perfect Line"

  override val valueType = MetricValueType.INT

  override val value: Double
    get() = sample.sum()

  override fun compute(sessions: List<Session>, comparator: SuggestionsComparator): Int {
    return sessions.count { it.success }
  }
}
