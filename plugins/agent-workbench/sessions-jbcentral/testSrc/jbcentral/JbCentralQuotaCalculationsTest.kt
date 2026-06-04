// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class JbCentralQuotaCalculationsTest {
  private val zone: ZoneId = ZoneId.of("UTC")

  private fun epochMillisAtStartOfDay(date: LocalDate, offsetMs: Long = 0L): Long =
    date.atStartOfDay(zone).toInstant().toEpochMilli() + offsetMs

  // ----- dailySpend -----

  @Test
  fun dailySpendWithEmptySamplesReturnsZeroPointsEndingAtToday() {
    val today = LocalDate.of(2026, 6, 10)
    val days = 3

    val points = dailySpend(samples = emptyList(), days = days, zone = zone, today = today)

    assertThat(points).hasSize(days)
    assertThat(points.map { it.spentUsd }).containsExactly(0.0, 0.0, 0.0)
    assertThat(points.last().date).isEqualTo(today)
    // oldest first, dates contiguous ending at today inclusive
    assertThat(points.map { it.date }).containsExactly(
      LocalDate.of(2026, 6, 8),
      LocalDate.of(2026, 6, 9),
      LocalDate.of(2026, 6, 10),
    )
  }

  @Test
  fun dailySpendBucketsPositiveDeltaToLocalDateOfLaterSample() {
    val today = LocalDate.of(2026, 6, 10)
    val jun8 = LocalDate.of(2026, 6, 8)
    val jun9 = LocalDate.of(2026, 6, 9)
    val jun10 = LocalDate.of(2026, 6, 10)

    val samples = listOf(
      UsageSample(timestampMs = epochMillisAtStartOfDay(jun8), usedUsd = 1.0),
      UsageSample(timestampMs = epochMillisAtStartOfDay(jun9), usedUsd = 4.0),
    )

    val points = dailySpend(samples = samples, days = 3, zone = zone, today = today)

    val byDate = points.associate { it.date to it.spentUsd }
    // delta 4.0 - 1.0 = 3.0 is bucketed to the LATER sample's local date (Jun 9)
    assertThat(byDate[jun9]).isEqualTo(3.0)
    assertThat(byDate[jun8]).isEqualTo(0.0)
    assertThat(byDate[jun10]).isEqualTo(0.0)
  }

  @Test
  fun dailySpendIgnoresNegativeDeltaButCountsSubsequentIncrease() {
    val today = LocalDate.of(2026, 6, 10)
    val jun8 = LocalDate.of(2026, 6, 8)
    val jun9 = LocalDate.of(2026, 6, 9)
    val jun10 = LocalDate.of(2026, 6, 10)

    val samples = listOf(
      UsageSample(timestampMs = epochMillisAtStartOfDay(jun8), usedUsd = 50.0),
      // billing reset: used drops 50 -> 2, negative delta ignored
      UsageSample(timestampMs = epochMillisAtStartOfDay(jun9), usedUsd = 2.0),
      // subsequent increase 2 -> 7 counts (delta 5.0) on Jun 10
      UsageSample(timestampMs = epochMillisAtStartOfDay(jun10), usedUsd = 7.0),
    )

    val points = dailySpend(samples = samples, days = 3, zone = zone, today = today)

    val byDate = points.associate { it.date to it.spentUsd }
    assertThat(byDate[jun8]).isEqualTo(0.0)
    assertThat(byDate[jun9]).isEqualTo(0.0)
    assertThat(byDate[jun10]).isEqualTo(5.0)
  }

  @Test
  fun dailySpendRoundsSummedDeltasToTwoDecimals() {
    val today = LocalDate.of(2026, 6, 10)
    val jun10 = LocalDate.of(2026, 6, 10)

    // Two increments landing on the same local date: 0.1 then 0.2.
    // 0.1 + 0.2 == 0.30000000000000004 in IEEE-754; round2 -> 0.3.
    val samples = listOf(
      UsageSample(timestampMs = epochMillisAtStartOfDay(jun10, 1L), usedUsd = 0.0),
      UsageSample(timestampMs = epochMillisAtStartOfDay(jun10, 2L), usedUsd = 0.1),
      UsageSample(timestampMs = epochMillisAtStartOfDay(jun10, 3L), usedUsd = 0.3),
    )

    val points = dailySpend(samples = samples, days = 1, zone = zone, today = today)

    assertThat(points).hasSize(1)
    assertThat(points.single().date).isEqualTo(jun10)
    assertThat(points.single().spentUsd).isEqualTo(0.3)
  }

  // ----- weekdaysBetween -----

  @Test
  fun weekdaysBetweenExcludesWeekendsOverAFullWeek() {
    // Mon Jun 1 .. (exclusive) Mon Jun 8, 2026 -> Mon-Fri counted, Sat/Sun excluded.
    val start = LocalDate.of(2026, 6, 1) // Monday
    val end = LocalDate.of(2026, 6, 8)   // next Monday (exclusive)

    assertThat(weekdaysBetween(start, end)).isEqualTo(5)
  }

  @Test
  fun weekdaysBetweenReturnsZeroWhenEndNotAfterStart() {
    val start = LocalDate.of(2026, 6, 8)
    assertThat(weekdaysBetween(start, start)).isEqualTo(0)
    assertThat(weekdaysBetween(start, start.minusDays(1))).isEqualTo(0)
  }

  @Test
  fun weekdaysBetweenIsHalfOpen() {
    // [Mon Jun 1, Wed Jun 3) -> Mon, Tue counted; Wed excluded.
    val start = LocalDate.of(2026, 6, 1) // Monday
    val end = LocalDate.of(2026, 6, 3)   // Wednesday (exclusive)

    assertThat(weekdaysBetween(start, end)).isEqualTo(2)
  }

  // ----- forecastSpend -----

  @Test
  fun forecastSpendReturnsNullForNonPositiveUsedOrTotal() {
    val reset = LocalDate.of(2026, 7, 1)
    val today = LocalDate.of(2026, 6, 5)

    assertThat(forecastSpend(usedUsd = 0.0, totalUsd = 200.0, resetDate = reset, today = today)).isNull()
    assertThat(forecastSpend(usedUsd = -1.0, totalUsd = 200.0, resetDate = reset, today = today)).isNull()
    assertThat(forecastSpend(usedUsd = 10.0, totalUsd = 0.0, resetDate = reset, today = today)).isNull()
    assertThat(forecastSpend(usedUsd = 10.0, totalUsd = -5.0, resetDate = reset, today = today)).isNull()
  }

  @Test
  fun forecastSpendSurplusWhenProjectionStaysUnderBudget() {
    // reset Wed Jul 1, 2026 -> period start Mon Jun 1, 2026.
    // today Fri Jun 5 -> endOfToday Sat Jun 6 -> elapsed weekdays [Jun 1, Jun 6) = 5.
    // remaining weekdays [Jun 6, Jul 1) = 17.
    val reset = LocalDate.of(2026, 7, 1)
    val today = LocalDate.of(2026, 6, 5)

    val forecast = forecastSpend(usedUsd = 10.0, totalUsd = 200.0, resetDate = reset, today = today)

    assertThat(forecast).isNotNull
    forecast!!
    assertThat(forecast.dailyRateUsd).isEqualTo(10.0 / 5) // usedUsd / elapsedWeekdays
    assertThat(forecast.outcome).isInstanceOf(JbCentralQuotaForecast.Outcome.Surplus::class.java)
    val surplus = forecast.outcome as JbCentralQuotaForecast.Outcome.Surplus
    // 200 - 10 - 2.0 * 17 = 156.0
    assertThat(surplus.remainingUsd).isEqualTo(156.0)
    assertThat(surplus.remainingUsd).isGreaterThan(0.0)
  }

  @Test
  fun forecastSpendRunsOutWhenBurnRateExceedsBudget() {
    val reset = LocalDate.of(2026, 7, 1)
    val today = LocalDate.of(2026, 6, 5)

    // used 100 over 5 weekdays -> rate 20/weekday; only 10 left, runs out fast.
    val forecast = forecastSpend(usedUsd = 100.0, totalUsd = 110.0, resetDate = reset, today = today)

    assertThat(forecast).isNotNull
    forecast!!
    assertThat(forecast.dailyRateUsd).isEqualTo(100.0 / 5)
    assertThat(forecast.outcome).isInstanceOf(JbCentralQuotaForecast.Outcome.RunsOut::class.java)
    val runsOut = forecast.outcome as JbCentralQuotaForecast.Outcome.RunsOut
    // budget 10, starting at Sat Jun 6: Sat/Sun skipped, Mon Jun 8 consumes the budget.
    assertThat(runsOut.date).isEqualTo(LocalDate.of(2026, 6, 8))
    assertThat(runsOut.date).isAfter(today)
    assertThat(runsOut.date).isBefore(reset)
    // run-out day is a weekday
    assertThat(runsOut.date.dayOfWeek.value).isLessThanOrEqualTo(5)
  }

  @Test
  fun forecastSpendReturnsNullWhenNoWeekdaysElapsed() {
    // reset Wed Jul 1 -> period start Mon Jun 1; today before start -> elapsed weekdays == 0.
    val reset = LocalDate.of(2026, 7, 1)
    val today = LocalDate.of(2026, 5, 30)

    assertThat(forecastSpend(usedUsd = 10.0, totalUsd = 200.0, resetDate = reset, today = today)).isNull()
  }

  // ----- parseResetDate -----

  @Test
  fun parseResetDateParsesUsEnglishFormat() {
    assertThat(parseResetDate("Jun 1, 2026")).isEqualTo(LocalDate.of(2026, 6, 1))
  }

  @Test
  fun parseResetDateParsesIsoFormat() {
    assertThat(parseResetDate("2026-06-01")).isEqualTo(LocalDate.of(2026, 6, 1))
  }

  @Test
  fun parseResetDateReturnsNullForBlankOrUnparseable() {
    assertThat(parseResetDate(null)).isNull()
    assertThat(parseResetDate("")).isNull()
    assertThat(parseResetDate("   ")).isNull()
    assertThat(parseResetDate("not a date")).isNull()
  }
}
