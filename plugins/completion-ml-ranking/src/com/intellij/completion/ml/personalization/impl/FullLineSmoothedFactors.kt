package com.intellij.completion.ml.personalization.impl

import com.intellij.completion.ml.personalization.*
import java.time.Instant
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.DurationUnit

private const val WAS_SELECTED = "was_selected"
private const val SELECTION = "selection"
private const val SHOW_UP = "show_up"
private val SMOOTHED_VALUES = listOf(SELECTION, SHOW_UP)

private const val GLOBAL_ACCEPTANCE_RATE = 0.2
private const val GLOBAL_ALPHA = 10

val HALF_LIFE_DURATIONS = listOf(1.hours, 1.days, 7.days)

fun lastTime(name: String) = "last_${name}_time"
fun smoothedCount(name: String, duration: Duration) = "${name}_count_smoothed_by_$duration"

class FullLineFactorsReader(factor: DailyAggregatedDoubleFactor) : UserFactorReaderBase(factor) {
  fun lastSelectionTimeToday(): Double? = getTodayFactor(lastTime(SELECTION))
  fun lastShowUpTimeToday(): Double? = getTodayFactor(lastTime(SHOW_UP))
  fun wasSelected(): Double? = getTodayFactor(WAS_SELECTED)

  private fun getTodayFactor(name: String) = factor.onDate(DateUtil.today())?.get(name)

  fun smoothedAcceptanceRate(duration: Duration): Double {
    val smoothed = factor.aggregateSmoothed(duration)
    return globallySmoothedRatio(smoothed[smoothedCount(SELECTION, duration)], smoothed[smoothedCount(SHOW_UP, duration)])
  }
}


class FullLineFactorsUpdater(factor: MutableDoubleFactor) : UserFactorUpdaterBase(factor) {
  fun fireLookupElementSelected() {
    factor.smoothedUpdate(SELECTION)
    factor.wasSelected(true)
  }

  fun fireLookupElementShowUp() {
    factor.smoothedUpdate(SHOW_UP)
    factor.wasSelected(false)
  }
}

class FullLineSmoothedAcceptanceRate(private val duration: Duration)
  : UserFactorBase<FullLineFactorsReader>("fullLineAcceptanceRateSmoothedBy$duration",
                                          UserFactorDescriptions.FULL_LINE_FACTORS) {
  override fun compute(reader: FullLineFactorsReader): String = reader.smoothedAcceptanceRate(duration).toString()
}

class FullLineLastSelectionTime
  : UserFactorBase<FullLineFactorsReader>("fullLineLastSelectionTime", UserFactorDescriptions.FULL_LINE_FACTORS) {
  override fun compute(reader: FullLineFactorsReader): String? = reader.lastSelectionTimeToday()?.let(::secondsToInstantString)

}

class FullLineLastShowUpTime
  : UserFactorBase<FullLineFactorsReader>("fullLineLastShowUpTime", UserFactorDescriptions.FULL_LINE_FACTORS) {
  override fun compute(reader: FullLineFactorsReader): String? = reader.lastShowUpTimeToday()?.let(::secondsToInstantString)
}

class FullLineWasSelected
  : UserFactorBase<FullLineFactorsReader>("fullLineWasSelected", UserFactorDescriptions.FULL_LINE_FACTORS) {
  override fun compute(reader: FullLineFactorsReader): String? = reader.wasSelected()?.toString()
}

private fun DailyAggregatedDoubleFactor.aggregateSmoothed(duration: Duration): Map<String, Double> {
  val result = mutableMapOf<String, Double>()
  val now = Instant.now().epochSecond.toDouble()
  for (onDate in availableDays().mapNotNull(this::onDate)) {
    for (name in SMOOTHED_VALUES) {
      val lastTime = onDate[lastTime(name)]
      val smoothedCountName = smoothedCount(name, duration)
      val smoothedCount = onDate[smoothedCountName]
      if (lastTime != null && smoothedCount != null) {
        result.compute(smoothedCountName) { _, old ->
          smoothValue(now - lastTime, duration, smoothedCount) + (old ?: 0.0)
        }
      }
    }
  }

  return result
}

private fun MutableDoubleFactor.smoothedUpdate(name: String) {
  val last_time = lastTime(name)
  updateOnDate(DateUtil.today()) {
    val now = Instant.now().epochSecond.toDouble()
    for (halfLifeDuration in HALF_LIFE_DURATIONS) {
      val smoothed_count = smoothedCount(name, halfLifeDuration)
      this[smoothed_count] = this[last_time]?.let { smoothValue(now - it, halfLifeDuration, this[smoothed_count]!!) + 1 } ?: 1.0
    }
    this[last_time] = now
  }
}

private fun MutableDoubleFactor.wasSelected(boolean: Boolean) {
  updateOnDate(DateUtil.today()) {
    this[WAS_SELECTED] = if (boolean) 1.0 else 0.0
  }
}

private fun smoothValue(duration: Double, halfLifeDuration: Duration, value: Double) = 0.5.pow(
  duration / halfLifeDuration.toDouble(DurationUnit.SECONDS)) * value

private fun globallySmoothedRatio(quotient: Double?, divisor: Double?) =
  if (divisor == null) GLOBAL_ACCEPTANCE_RATE
  else ((quotient ?: 0.0) + GLOBAL_ACCEPTANCE_RATE * GLOBAL_ALPHA) / (divisor + GLOBAL_ALPHA)

private fun secondsToInstantString(seconds: Double) = Instant.ofEpochSecond(seconds.toLong()).toString()
