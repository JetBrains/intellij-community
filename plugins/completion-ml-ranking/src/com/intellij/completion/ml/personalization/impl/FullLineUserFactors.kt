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

val DURATIONS = listOf(1.hours, 1.days, 7.days)

fun lastTimeName(name: String) = "last_${name}_time"
fun decayingNumberName(name: String, duration: Duration) = "${name}_number_decayed_by_$duration"

class FullLineFactorsReader(factor: DailyAggregatedDoubleFactor) : UserFactorReaderBase(factor) {
  fun lastSelectionTimeToday(): Double? = getTodayFactor(lastTimeName(SELECTION))
  fun lastShowUpTimeToday(): Double? = getTodayFactor(lastTimeName(SHOW_UP))
  fun wasSelected(): Double? = getTodayFactor(WAS_SELECTED)

  private fun getTodayFactor(name: String) = factor.onDate(DateUtil.today())?.get(name)

  fun smoothedAcceptanceRate(duration: Duration): Double {
    return globallySmoothedRatio(selectionNumberDecayedBy(duration), showUpNumberDecayedBy(duration))
  }
  
  fun selectionNumberDecayedBy(duration: Duration) = factor.aggregateDecayingNumber(SELECTION, duration)
  fun showUpNumberDecayedBy(duration: Duration) = factor.aggregateDecayingNumber(SHOW_UP, duration)
}


class FullLineFactorsUpdater(factor: MutableDoubleFactor) : UserFactorUpdaterBase(factor) {
  fun fireLookupElementSelected() {
    factor increment SELECTION
    factor.wasSelected(true)
  }

  fun fireLookupElementShowUp() {
    factor increment SHOW_UP
    factor.wasSelected(false)
  }
}

class FullLineSmoothedAcceptanceRate(private val duration: Duration)
  : UserFactorBase<FullLineFactorsReader>("fullLineAcceptanceRateSmoothedBy$duration",
                                          UserFactorDescriptions.FULL_LINE_FACTORS) {
  override fun compute(reader: FullLineFactorsReader): String = reader.smoothedAcceptanceRate(duration).toString()
}

class FullLineSelectionNumberDecayedBy(private val duration: Duration)
  : UserFactorBase<FullLineFactorsReader>("fullLineSelectionNumberDecayedBy$duration", UserFactorDescriptions.FULL_LINE_FACTORS) {
  override fun compute(reader: FullLineFactorsReader): String = reader.selectionNumberDecayedBy(duration).toString()
}

class FullLineShowUpNumberDecayedBy(private val duration: Duration)
  : UserFactorBase<FullLineFactorsReader>("fullLineShowUpNumberDecayedBy$duration", UserFactorDescriptions.FULL_LINE_FACTORS) {
  override fun compute(reader: FullLineFactorsReader): String = reader.showUpNumberDecayedBy(duration).toString()
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

private fun DailyAggregatedDoubleFactor.aggregateDecayingNumber(name: String, duration: Duration): Double {
  var result = 0.0
  val lastTimeName = lastTimeName(name)
  val decayingNumberName = decayingNumberName(name, duration)
  val now = getLastTimeOrNow(lastTimeName)
  for (onDate in availableDays().mapNotNull(this::onDate)) {
    val lastTime = onDate[lastTimeName]
    val dacayingNumber = onDate[decayingNumberName]
    if (lastTime != null) {
      result += dacayingNumber.decay(now - lastTime, duration)
    }
  }
  return result
}

private fun DailyAggregatedDoubleFactor.getLastTimeOrNow(name: String) = onDate(DateUtil.today())?.get(name)
                                                                         ?: Instant.now().epochSecond.toDouble()

private infix fun MutableDoubleFactor.increment(name: String) {
  val last_time = lastTimeName(name)
  updateOnDate(DateUtil.today()) {
    val now = Instant.now().epochSecond.toDouble()
    for (duration in DURATIONS) {
      val decayingNumber = decayingNumberName(name, duration)
      this[decayingNumber] = this[last_time]?.let { this[decayingNumber].decay(now - it, duration) + 1 } ?: 1.0
    }
    this[last_time] = now
  }
}

private fun MutableDoubleFactor.wasSelected(boolean: Boolean) {
  updateOnDate(DateUtil.today()) {
    this[WAS_SELECTED] = if (boolean) 1.0 else 0.0
  }
}

private fun Double?.decay(duration: Double, halfLifeDuration: Duration) =
  if (this == null) 0.0
  else if (duration * this == 0.0) this
  else 0.5.pow(duration / halfLifeDuration.toDouble(DurationUnit.SECONDS)) * this

private fun globallySmoothedRatio(quotient: Double?, divisor: Double?) =
  if (divisor == null) GLOBAL_ACCEPTANCE_RATE
  else ((quotient ?: 0.0) + GLOBAL_ACCEPTANCE_RATE * GLOBAL_ALPHA) / (divisor + GLOBAL_ALPHA)

private fun timeSince(epochSeconds: Double) = (Instant.now().epochSecond - epochSeconds.toLong()).toString()
