package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

abstract class CompletionGolfMetric<T : Number> : Metric {
  protected var sample = Sample()

  private fun T.alsoAddToSample(): T = also { sample.add(it.toDouble()) }

  override fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator): T = compute(sessions, comparator).alsoAddToSample()

  abstract fun compute(sessions: List<Session>, comparator: SuggestionsComparator): T
}

class CGMovesCount : CompletionGolfMetric<Int>() {
  override val name: String = "Total Moves"

  override val valueType = MetricValueType.INT

  private val metrics = listOf(
    CGTypingsCount(),
    CGNavigationsCount(),
    CGCompletionInvocationsCount()
  )

  override val value: Double
    get() = sample.sum()

  override fun compute(sessions: List<Session>, comparator: SuggestionsComparator): Int {
    // Add x2 amount of lookups, assuming that before each action we call code completion
    // We summarize 3 types of actions:
    // call code completion (1 point)
    // choice suggestion from completion or symbol (if there is no offer in completion) (1 point)
    // navigation to the suggestion (if it fits) (N points, based on suggestion index, assuming first index is 0)
    return metrics.sumOf { it.compute(sessions, comparator) }
  }
}

class CGTypingsCount : CompletionGolfMetric<Int>() {
  override val name: String = "Typings"

  override val valueType = MetricValueType.INT

  override val value: Double
    get() = sample.sum()

  override fun compute(sessions: List<Session>, comparator: SuggestionsComparator): Int =
    sessions.sumOf { it.lookups.count() }
}

class CGNavigationsCount : CompletionGolfMetric<Int>() {
  override val name: String = "Navigations"

  override val valueType = MetricValueType.INT

  override val value: Double
    get() = sample.sum()

  override fun compute(sessions: List<Session>, comparator: SuggestionsComparator): Int =
    sessions.sumOf { computeMoves(it) }

  private fun computeMoves(session: Session): Int = session.lookups.sumOf { if (it.selectedPosition >= 0) it.selectedPosition else 0 }
}


class CGCompletionInvocationsCount : CompletionGolfMetric<Int>() {
  override val name: String = "Completion Invocations"

  override val valueType = MetricValueType.INT

  override val value: Double
    get() = sample.sum()

  override fun compute(sessions: List<Session>, comparator: SuggestionsComparator): Int =
    sessions.sumOf { it.lookups.count { lookup -> lookup.isNew } }
}

class CGMovesCountNormalised : CompletionGolfMetric<Double>() {
  override val name: String = "Moves Count Normalised"

  override val valueType = MetricValueType.DOUBLE

  override val value: Double
    get() = sample.mean()

  override fun compute(sessions: List<Session>, comparator: SuggestionsComparator): Double {
    val linesLength = sessions.sumOf { it.expectedText.length } * 2.0
    val movesCount = CGMovesCount().compute(sessions, comparator)

    val subtrahend = sessions.count() * 2.0

    // Since code completion's call and the choice of option (symbol) contains in each lookup,
    // It is enough to calculate the difference for the number of lookups and extra moves (for completion case)
    // To reach 0%, you also need to subtract the minimum number of lookups (eq. number of sessions plus minimum amount of completion calls)
    // 0% - best scenario, every line was completed from start to end with first suggestion in list
    // >100% is possible, when navigation in completion takes too many moves
    return ((movesCount - subtrahend) / (linesLength - subtrahend))
  }
}

class CGPerfectLine : CompletionGolfMetric<Int>() {
  override val name: String = "Perfect Line"

  override val valueType = MetricValueType.INT

  override val value: Double
    get() = sample.sum()

  override fun compute(sessions: List<Session>, comparator: SuggestionsComparator): Int {
    return sessions.count { it.success }
  }
}

class CGRecallAt(private val n: Int) : Metric {
  private val sample = Sample()

  override val name: String = "Recall@$n"

  override val valueType = MetricValueType.DOUBLE

  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator): Double {
    val fileSample = Sample()

    for (lookup in sessions.flatMap { it.lookups }) {
      if (lookup.selectedPosition in 0 until n) {
        fileSample.add(1.0)
        sample.add(1.0)
      } else {
        fileSample.add(0.0)
        sample.add(0.0)
      }
    }
    return fileSample.mean()
  }
}
