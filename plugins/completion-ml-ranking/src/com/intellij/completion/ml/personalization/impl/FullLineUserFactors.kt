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

private const val GLOBAL_ACCEPTANCE_RATE = 0.2
private const val GLOBAL_ALPHA = 10

val DECAY_DURATIONS = listOf(1.hours, 1.days, 7.days)

fun lastTimeName(name: String) = "last_${name}_time"
fun decayingCountName(name: String, decayDuration: Duration) = "${name}_count_decayed_by_$decayDuration"

class FullLineFactorsReader(factor: DailyAggregatedDoubleFactor) : UserFactorReaderBase(factor) {
  fun lastSelectionTimeToday(): Double? = getTodayFactor(lastTimeName(SELECTION))
  fun lastShowUpTimeToday(): Double? = getTodayFactor(lastTimeName(SHOW_UP))
  fun wasSelected(): Double? = getTodayFactor(WAS_SELECTED)

  private fun getTodayFactor(name: String) = factor.onDate(DateUtil.today())?.get(name)

  fun smoothedAcceptanceRate(decayDuration: Duration): Double {
    val timestamp = currentEpochSeconds()
    return globallySmoothedRatio(selectionCountDecayedBy(decayDuration, timestamp), showUpCountDecayedBy(decayDuration, timestamp))
  }

  fun selectionCountDecayedBy(decayDuration: Duration, timestamp: Double = currentEpochSeconds()) =
    factor.aggregateDecayingCount(SELECTION, decayDuration, timestamp)

  fun showUpCountDecayedBy(decayDuration: Duration, timestamp: Double = currentEpochSeconds()) =
    factor.aggregateDecayingCount(SHOW_UP, decayDuration, timestamp)
}

private fun currentEpochSeconds() = Instant.now().epochSecond.toDouble()


class FullLineFactorsUpdater(factor: MutableDoubleFactor) : UserFactorUpdaterBase(factor) {
  fun fireLookupElementSelected() {
    val timestamp = currentEpochSeconds()
    for (duration in DECAY_DURATIONS) {
      factor.increment(SELECTION, duration, timestamp)
    }
    factor.wasSelected(true)
  }

  fun fireLookupElementShowUp() {
    val timestamp = currentEpochSeconds()
    for (duration in DECAY_DURATIONS) {
      factor.increment(SHOW_UP, duration, timestamp)
    }
    factor.wasSelected(false)
  }
}

class FullLineSmoothedAcceptanceRate(private val duration: Duration)
  : UserFactorBase<FullLineFactorsReader>("fullLineAcceptanceRateSmoothedBy$duration",
                                          UserFactorDescriptions.FULL_LINE_FACTORS) {
  override fun compute(reader: FullLineFactorsReader): String = reader.smoothedAcceptanceRate(duration).toString()
}

class FullLineSelectionCountDecayedBy(private val duration: Duration)
  : UserFactorBase<FullLineFactorsReader>("fullLineSelectionCountDecayedBy$duration", UserFactorDescriptions.FULL_LINE_FACTORS) {
  override fun compute(reader: FullLineFactorsReader): String = reader.selectionCountDecayedBy(duration).toString()
}

class FullLineShowUpCountDecayedBy(private val duration: Duration)
  : UserFactorBase<FullLineFactorsReader>("fullLineShowUpCountDecayedBy$duration", UserFactorDescriptions.FULL_LINE_FACTORS) {
  override fun compute(reader: FullLineFactorsReader): String = reader.showUpCountDecayedBy(duration).toString()
}

class FullLineTimeSinceLastSelection
  : UserFactorBase<FullLineFactorsReader>("fullLineTimeSinceLastSelection", UserFactorDescriptions.FULL_LINE_FACTORS) {
  override fun compute(reader: FullLineFactorsReader): String? = reader.lastSelectionTimeToday()?.let(::timeSince)

}

class FullLineTimeSinceLastShowUp
  : UserFactorBase<FullLineFactorsReader>("fullLineTimeSinceLastShowUp", UserFactorDescriptions.FULL_LINE_FACTORS) {
  override fun compute(reader: FullLineFactorsReader): String? = reader.lastShowUpTimeToday()?.let(::timeSince)
}

class FullLineWasSelected
  : UserFactorBase<FullLineFactorsReader>("fullLineWasSelected", UserFactorDescriptions.FULL_LINE_FACTORS) {
  override fun compute(reader: FullLineFactorsReader): String? = reader.wasSelected()?.toString()
}

fun DailyAggregatedDoubleFactor.aggregateDecayingCount(name: String, decayDuration: Duration, timestamp: Double): Double {
  var result = 0.0
  for (day in availableDays()) {
    result += get(name, decayDuration, day, timestamp) ?: 0.0
  }
  return result
}

fun DailyAggregatedDoubleFactor.get(name: String, decayDuration: Duration, day: Day, timestamp: Double): Double? {
  val onDate = onDate(day) ?: return null
  val lastTimeName = lastTimeName(name)
  val decayingCountName = decayingCountName(name, decayDuration)
  val lastTime = onDate[lastTimeName] ?: return null
  val decayingCount = onDate[decayingCountName]
  return decayingCount.decay(timestamp - lastTime, decayDuration)
}

fun MutableDoubleFactor.increment(name: String, decayDuration: Duration, timestamp: Double) {
  updateOnDate(DateUtil.today()) {
    val lastTimeName = lastTimeName(name)
    val decayingCountName = decayingCountName(name, decayDuration)
    this[decayingCountName] = this[lastTimeName]?.let { this[decayingCountName].decay(timestamp - it, decayDuration) + 1 } ?: 1.0
    this[lastTimeName] = timestamp
  }
}

private fun MutableDoubleFactor.wasSelected(boolean: Boolean) {
  updateOnDate(DateUtil.today()) {
    this[WAS_SELECTED] = if (boolean) 1.0 else 0.0
  }
}

private fun Double?.decay(duration: Double, decayDuration: Duration) =
  if (this == null) 0.0
  else if (duration * this == 0.0) this
  else 0.5.pow(duration / decayDuration.toDouble(DurationUnit.SECONDS)) * this

private fun globallySmoothedRatio(quotient: Double?, divisor: Double?) =
  if (divisor == null) GLOBAL_ACCEPTANCE_RATE
  else ((quotient ?: 0.0) + GLOBAL_ACCEPTANCE_RATE * GLOBAL_ALPHA) / (divisor + GLOBAL_ALPHA)

private fun timeSince(epochSeconds: Double) = (Instant.now().epochSecond - epochSeconds.toLong()).toString()
