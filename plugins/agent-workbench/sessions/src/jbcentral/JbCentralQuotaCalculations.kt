// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.round

@Serializable
internal data class UsageSample(
  @JvmField val timestampMs: Long,
  @JvmField val usedUsd: Double,
)

internal data class DailyUsagePoint(val date: LocalDate, val spentUsd: Double)

/** Port of JBCentralGUI `src-tauri/src/usage_history.rs` `daily()`. */
internal fun dailySpend(
  samples: List<UsageSample>,
  days: Int,
  zone: ZoneId = ZoneId.systemDefault(),
  today: LocalDate = LocalDate.now(zone),
): List<DailyUsagePoint> {
  val sorted = samples.sortedBy { it.timestampMs }
  val buckets = HashMap<LocalDate, Double>()
  for (i in 1 until sorted.size) {
    val prev = sorted[i - 1]
    val curr = sorted[i]
    val delta = curr.usedUsd - prev.usedUsd
    if (delta <= 0.0) continue
    val date = Instant.ofEpochMilli(curr.timestampMs).atZone(zone).toLocalDate()
    buckets[date] = (buckets[date] ?: 0.0) + delta
  }
  return (days - 1 downTo 0).map { offset ->
    val date = today.minusDays(offset.toLong())
    DailyUsagePoint(date, round2(buckets[date] ?: 0.0))
  }
}

internal data class JbCentralQuotaForecast(
  val dailyRateUsd: Double,            // $ per weekday so far this period
  val outcome: Outcome,
) {
  internal sealed interface Outcome {
    data class Surplus(val remainingUsd: Double) : Outcome   // projected to last with $X to spare
    data class RunsOut(val date: LocalDate) : Outcome  // projected to run out on this date
  }
}

/** Count days `d` in `[startInclusive, endExclusive)` whose day of week is not Saturday or Sunday. */
internal fun weekdaysBetween(startInclusive: LocalDate, endExclusive: LocalDate): Int {
  if (!endExclusive.isAfter(startInclusive)) return 0
  var count = 0
  var day = startInclusive
  while (day.isBefore(endExclusive)) {
    if (!day.isWeekend()) count++
    day = day.plusDays(1)
  }
  return count
}

/** Port of JBCentralGUI `src/spend-projection.ts` `projectSpend`. */
internal fun forecastSpend(
  usedUsd: Double,
  totalUsd: Double,
  resetDate: LocalDate,
  today: LocalDate = LocalDate.now(),
): JbCentralQuotaForecast? {
  if (totalUsd <= 0.0 || usedUsd <= 0.0) return null
  val start = resetDate.minusMonths(1)        // period start = reset − 1 month
  val endOfToday = today.plusDays(1)          // exclusive bound that counts today as elapsed
  val elapsed = weekdaysBetween(start, endOfToday)
  if (elapsed == 0) return null
  val rate = usedUsd / elapsed
  if (rate == 0.0) return null
  val remainingWeekdays = weekdaysBetween(endOfToday, resetDate)
  val surplus = totalUsd - usedUsd - rate * remainingWeekdays
  if (surplus >= 0.0) {
    return JbCentralQuotaForecast(rate, JbCentralQuotaForecast.Outcome.Surplus(surplus))
  }
  var day = endOfToday
  var budget = totalUsd - usedUsd
  while (budget > 0.0) {
    if (!day.isWeekend()) budget -= rate
    if (budget > 0.0) day = day.plusDays(1)
  }
  return JbCentralQuotaForecast(rate, JbCentralQuotaForecast.Outcome.RunsOut(day))
}

/** Parses a reset-date string like `"Jun 1, 2026"` (US English). Returns null on blank/unparseable input. */
internal fun parseResetDate(text: String?): LocalDate? {
  val trimmed = text?.trim()
  if (trimmed.isNullOrBlank()) return null
  for (formatter in RESET_DATE_FORMATTERS) {
    try {
      return LocalDate.parse(trimmed, formatter)
    }
    catch (_: DateTimeParseException) {
      // try next formatter
    }
  }
  return try {
    LocalDate.parse(trimmed)
  }
  catch (_: DateTimeParseException) {
    null
  }
}

private val RESET_DATE_FORMATTERS = listOf(
  DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US),
  DateTimeFormatter.ofPattern("MMM d yyyy", Locale.US),
)

private fun LocalDate.isWeekend(): Boolean =
  dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

private fun round2(x: Double): Double = round(x * 100) / 100
