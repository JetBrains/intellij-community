// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.utils

import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.annotations.TestOnly
import java.text.SimpleDateFormat
import java.time.ZoneOffset
import java.util.*
import kotlin.math.*

object StatisticsUtil {
  private const val kilo = 1000
  private const val mega = kilo * kilo

  @JvmStatic
  fun addPluginInfoTo(info: PluginInfo, data: MutableMap<String, Any>) {
    data["plugin_type"] = info.type.name
    if (!info.type.isSafeToReport()) return
    val id = info.id
    if (!id.isNullOrEmpty()) {
      data["plugin"] = id
    }
    val version = info.version
    if (!version.isNullOrEmpty()) {
      data["plugin_version"] = version
    }
  }

  /**
   * Anonymizes sensitive project properties by rounding it to the next power of two.
   *
   * Special cases:
   *  - returns the same value if it is a power of two;
   *  - returns `-roundToPowerOfTwo(abs(value))` if the value is negative;
   *  - returns 0 in case of 0;
   *  - returns Int.MAX_VALUE if next power of two is bigger than Int.MAX_VALUE;
   *  - returns Int.MIN_VALUE if next power of two is smaller than Int.MIN_VALUE.
   */
  @JvmStatic
  fun roundToPowerOfTwo(value: Int): Int {
    if (value == 0) return 0
    val abs = abs(value)
    if (abs == 1) return value
    val nextPowerOfTwo = Integer.highestOneBit(abs - 1) shl 1
    if (nextPowerOfTwo < 0) {
      //overflow
      if (value > 0) return Int.MAX_VALUE else return Int.MIN_VALUE
    }
    return Integer.signum(value) * nextPowerOfTwo
  }

  /**
   * @see com.intellij.internal.statistic.utils.StatisticsUtil.roundToPowerOfTwo(int)
   */
  @JvmStatic
  fun roundToPowerOfTwo(value: Long): Long {
    if (value == 0L) return 0
    val abs = abs(value)
    if (abs == 1L) return value
    val nextPowerOfTwo = java.lang.Long.highestOneBit(abs - 1) shl 1
    if (nextPowerOfTwo < 0) {
      //overflow
      if (value > 0) return Long.MAX_VALUE else return Long.MIN_VALUE
    }
    return java.lang.Long.signum(value) * nextPowerOfTwo
  }

  /**
   * Please, use only in specific cases where precision of `roundToPowerOfTwo` is not enough
   *
   * Anonymizes sensitive project properties by rounding it to the power of ten multiplied with either:
   *  - leading digit if second digit is smaller than 4 or equal to 4
   *  - leading digit plus one if second digit is greater than 5 or equal to 5
   *
   * Special cases:
   *  - returns the same value if all trailing digits equal zero;
   *  - returns `-roundToHighestDigit(abs(value))` if the value is negative;
   *  - returns 0 in case of 0;
   *  - returns +/-1 for abs(value) in 1..4
   *  - returns +/-10 for abs(value) in 5..10
   *  - returns `roundToHighestDigit(Int.MAX_VALUE)` for Int.MIN_VALUE
   */
  @JvmStatic
  fun roundToHighestDigit(value: Int): Int {
    if (value == 0) return 0
    val abs = if (value != Int.MIN_VALUE) abs(value) else Int.MAX_VALUE // See abs(Int) documentation
    if (abs <= 4) return Integer.signum(value) * 1
    else if (abs <= 10) return Integer.signum(value) * 10

    var firstDigit = abs.toFloat()
    var tenPowX = 1
    while (firstDigit > 10) {
      firstDigit /= 10
      tenPowX *= 10
    }

    // can't get overflow because second digit in max int (2147483647) is '1'
    return Integer.signum(value) * (firstDigit.roundToInt() * tenPowX)
  }

  /**
   * Please, use only in specific cases where precision of `roundToPowerOfTwo` is not enough
   *
   *  @see com.intellij.internal.statistic.utils.StatisticsUtil.roundToHighestDigit(int)
   */
  @JvmStatic
  fun roundToHighestDigit(value: Long): Long {
    if (value == 0L) return 0L
    val abs = if (value != Long.MIN_VALUE) abs(value) else Long.MAX_VALUE // See abs(Long) documentation
    if (abs <= 4L) return java.lang.Long.signum(value) * 1L
    else if (abs <= 10L) return java.lang.Long.signum(value) * 10L

    var firstDigit = abs.toDouble()
    var tenPowX = 1L
    while (firstDigit > 10L) {
      firstDigit /= 10L
      tenPowX *= 10L
    }

    // can't get overflow because second digit in max long (9223372036854775807L) is '2'
    return java.lang.Long.signum(value) * (firstDigit.roundToLong() * tenPowX)
  }

  /**
   * Anonymizes value by finding upper bound in provided bounds.
   * Allows more fine tuning then `com.intellij.internal.statistic.utils.StatisticsUtil#roundToPowerOfTwo`
   * but requires manual maintaining.
   *
   * @param bounds is an integer array sorted in ascending order (required, but not checked)
   * @return value upper bound or next power of two if no bounds were provided (as fallback)
   * */
  @JvmStatic
  @Deprecated(message = "Use com.intellij.internal.statistic.eventLog.events.EventFields.BoundedInt instead", replaceWith = ReplaceWith("roundToUpperBoundInternal"))
  @ScheduledForRemoval
  fun roundToUpperBound(value: Int, bounds: IntArray): Int {
    return roundToUpperBoundInternal(value, bounds)
  }

  /**
   * Anonymizes value by finding upper bound in provided bounds.
   * Allows to have better precision then [com.intellij.internal.statistic.utils.StatisticsUtil.roundToPowerOfTwo]
   * but requires manual maintaining.
   *
   * Empty bounds and ordering aren't checked - secured with check in [com.intellij.internal.statistic.eventLog.events.BoundedIntEventField]
   *
   * @param bounds is a non-empty integer array sorted in ascending order (required, but not checked)
   * @return value upper bound
   * */
  @JvmStatic
  internal fun roundToUpperBoundInternal(value: Int, bounds: IntArray): Int {
    val insertIndex = bounds.binarySearch(value, 0, bounds.size)
    return if (insertIndex >= 0) bounds[insertIndex]
    else {
      val revertedIndex = -(insertIndex + 1)
      if(revertedIndex < bounds.size) bounds[revertedIndex] else bounds.last()
    }
  }

  @TestOnly
  fun roundToUpperBoundInternalTest(value: Int, bounds: IntArray): Int = roundToUpperBoundInternal(value, bounds)

  /**
   * Anonymizes value by finding upper bound in provided bounds.
   * Allows to have better precision then [com.intellij.internal.statistic.utils.StatisticsUtil.roundToPowerOfTwo]
   * but requires manual maintaining.
   *
   * Empty bounds and ordering aren't checked - secured with check in [com.intellij.internal.statistic.eventLog.events.BoundedLongEventField]
   *
   * @param bounds is a non-empty integer array sorted in ascending order (required, but not checked)
   * @return value upper bound
   * */
  @JvmStatic
  internal fun roundToUpperBoundInternal(value: Long, bounds: LongArray): Long {
    val insertIndex = bounds.binarySearch(value, 0, bounds.size)
    return if (insertIndex >= 0) bounds[insertIndex]
    else {
      val revertedIndex = -(insertIndex + 1)
      if(revertedIndex < bounds.size) bounds[revertedIndex] else bounds.last()
    }
  }

  @TestOnly
  fun roundToUpperBoundInternalTest(value: Long, bounds: LongArray): Long = roundToUpperBoundInternal(value, bounds)

  /**
   * Anonymizes sensitive project properties by rounding it to the next value in steps list.
   * See `com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsagesCollector`
   */
  fun getCountingStepName(value: Int, steps: List<Int>): String {
    if (steps.isEmpty()) return value.toString()
    if (value < steps[0]) return "<" + steps[0]

    var stepIndex = 0
    while (stepIndex < steps.size - 1) {
      if (value < steps[stepIndex + 1]) break
      stepIndex++
    }

    val step = steps[stepIndex]
    val addPlus = stepIndex == steps.size - 1 || steps[stepIndex + 1] != step + 1
    return humanize(step) + if (addPlus) "+" else ""
  }

  /**
   * Returns current hour in UTC as "yyMMddHH"
   */
  fun getCurrentHourInUTC(calendar: Calendar = Calendar.getInstance(Locale.ENGLISH)): String {
    calendar[Calendar.YEAR] = calendar[Calendar.YEAR].coerceIn(2000, 2099)
    val format = SimpleDateFormat("yyMMddHH", Locale.ENGLISH)
    format.timeZone = TimeZone.getTimeZone(ZoneOffset.UTC)
    return format.format(calendar.time)
  }

  /**
   * Returns provided timestamp date in UTC as "yyMMdd"
   */
  fun getTimestampDateInUTC(timestamp: Long, calendar: Calendar = Calendar.getInstance(Locale.ENGLISH)): String {
    calendar.timeInMillis = timestamp
    calendar[Calendar.YEAR] = calendar[Calendar.YEAR].coerceIn(2000, 2099)
    val format = SimpleDateFormat("yyMMdd", Locale.ENGLISH)
    format.timeZone = TimeZone.getTimeZone(ZoneOffset.UTC)
    return format.format(calendar.time)
  }

  private fun humanize(number: Int): String {
    if (number == 0) return "0"
    val m = number / mega
    val k = (number % mega) / kilo
    val r = (number % kilo)
    val ms = if (m > 0) "${m}M" else ""
    val ks = if (k > 0) "${k}K" else ""
    val rs = if (r > 0) "${r}" else ""
    return ms + ks + rs
  }

  /**
   * If multiple events with duration will happen one after another, we won't merge them if they have different duration,
   * e.g. EditorRight happens in big batches
   */
  fun roundDuration(durationMs: Long): Long {
    if (durationMs >= 100 || durationMs < 0) {
      // negative durations shouldn't happen but if they are we want to see it
      return (durationMs / 100) * 100
    }
    return if (durationMs >= 50) 50 else 0
  }

  internal fun Int.roundLogarithmic() = decomposed10 { s, m, base ->
    s * m.roundMantis().toInt() * base.roundToInt()
  }

  @TestOnly
  fun Int.roundLogarithmicTest() = roundLogarithmic()

  internal fun Long.roundLogarithmic() = decomposed10 { s, m, base ->
    s * m.roundMantis().toLong() * base.roundToLong()
  }

  @TestOnly
  fun Long.roundLogarithmicTest() = roundLogarithmic()

  private val roundValues: Array<Double> = arrayOf(1.0, 2.0, 5.0, 10.0)

  private fun Double.roundMantis(): Double {
    var prev = 0.0
    roundValues.forEach {
      if (this < (it + prev) / 2.0) {
        return prev
      }
      prev = it
    }
    return 10.0
  }

  private inline fun <T> Number.decomposed10(processor: (sign: Int, m: Double, base: Double) -> T): T {
    val v = toDouble()
    val lg = v.intLog10()
    val base = 10.0.pow(lg)
    return processor(v.sign.toInt(), v.absoluteValue / base, base)
  }

  private fun Double.intLog10(): Int = absoluteValue.let {
    if (it < 10) 0 else truncate(log10(it)).toInt()
  }
}