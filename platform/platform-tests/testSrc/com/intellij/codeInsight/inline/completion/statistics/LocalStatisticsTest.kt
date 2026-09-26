// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.statistics

import com.intellij.codeInsight.inline.completion.statistics.LocalStatistics.Companion.getInstance
import com.intellij.codeInsight.inline.completion.statistics.LocalStatistics.Date
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.time.LocalDate

class LocalStatisticsTest : BasePlatformTestCase() {

  private lateinit var statistics: LocalStatistics

  override fun setUp() {
    super.setUp()
    statistics = getInstance()
    // Clear any existing statistics
    statistics.loadState(LocalStatistics.State())
    statistics.forcePrune.set(true)
  }

  override fun tearDown() {
    try {
      statistics.forcePrune.set(false)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testBasicStatisticsRecording() {
    val testField = EventFields.Int("test_field")
    LocalStatistics.Schema.register(testField)

    statistics.saveIfRegistered(EventPair(testField, 10))
    statistics.saveIfRegistered(EventPair(testField, 20))

    val state = statistics.state
    val statsForToday = state[Date.now()]
    assertNotNull("Statistics for today should exist", statsForToday)

    val fieldStats = statsForToday!!.values["test_field"]
    assertNotNull("Statistics for test_field should exist", fieldStats)
    assertEquals("Count should be 2", 2, fieldStats!!.count)
    assertEquals("Sum should be 30.0", 30.0f, fieldStats.sum, 0.001f)
  }

  fun testBooleanStatisticsRecording() {
    val testField = EventFields.Boolean("test_bool_field")
    LocalStatistics.Schema.register(testField)

    statistics.saveIfRegistered(EventPair(testField, true))
    statistics.saveIfRegistered(EventPair(testField, false))
    statistics.saveIfRegistered(EventPair(testField, true))

    val state = statistics.state
    val statsForToday = state[Date.now()]
    assertNotNull("Statistics for today should exist", statsForToday)

    val fieldStats = statsForToday!!.values["test_bool_field"]
    assertNotNull("Statistics for test_bool_field should exist", fieldStats)
    assertEquals("Count should be 3", 3, fieldStats!!.count)

    assertEquals("Should have 2 true values", 2, fieldStats.distribution["true"])
    assertEquals("Should have 1 false value", 1, fieldStats.distribution["false"])
  }

  fun testEnumStatisticsRecording() {
    val testField = EventFields.Enum("test_enum_field", TestEnum::class.java)
    LocalStatistics.Schema.register(testField)

    statistics.saveIfRegistered(EventPair(testField, TestEnum.VALUE1))
    statistics.saveIfRegistered(EventPair(testField, TestEnum.VALUE2))
    statistics.saveIfRegistered(EventPair(testField, TestEnum.VALUE1))

    val state = statistics.state
    val statsForToday = state[Date.now()]
    assertNotNull("Statistics for today should exist", statsForToday)

    val fieldStats = statsForToday!!.values["test_enum_field"]
    assertNotNull("Statistics for test_enum_field should exist", fieldStats)
    assertEquals("Count should be 3", 3, fieldStats!!.count)

    assertEquals("Should have 2 VALUE1 values", 2, fieldStats.distribution["VALUE1"])
    assertEquals("Should have 1 VALUE2 value", 1, fieldStats.distribution["VALUE2"])
  }

  fun testUnregisteredFieldIsIgnored() {
    val unregisteredField = EventFields.Int("unregistered_field")

    statistics.saveIfRegistered(EventPair(unregisteredField, 10))

    val state = statistics.state
    val statsForToday = state[Date.now()]

    if (statsForToday != null) {
      assertNull("Statistics for unregistered_field should not exist", statsForToday.values["unregistered_field"])
    }
  }

  fun testWithMockedCurrentDate() {
    val testField = EventFields.Int("test_field_mocked_date")
    LocalStatistics.Schema.register(testField)

    val mockedDate = Date(2023, 1, 1, 1)
    statistics.withMockedCurrentDate(mockedDate) {
      saveIfRegistered(EventPair(testField, 10))
    }

    val state = statistics.state
    val statsForMockedDate = state[mockedDate]
    assertNotNull("Statistics for mocked date should exist", statsForMockedDate)

    val fieldStats = statsForMockedDate!!.values["test_field_mocked_date"]
    assertNotNull("Statistics for test_field_mocked_date should exist", fieldStats)
    assertEquals("Count should be 1", 1, fieldStats!!.count)
    assertEquals("Sum should be 10.0", 10.0f, fieldStats.sum, 0.001f)
  }

  fun testPersistenceAcrossDays() {
    val testField = EventFields.Int("test_field_across_days")
    LocalStatistics.Schema.register(testField)

    val testState = LocalStatistics.State()

    // Save statistics for day 1
    val day1 = Date(2023, 1, 1, 1)
    statistics.loadState(testState)
    statistics.withMockedCurrentDate(day1) {
      saveIfRegistered(EventPair(testField, 10))
    }

    val fieldStatsDay1 = statistics.state[day1]!!.values["test_field_across_days"]
    assertNotNull("Statistics for test_field_across_days on day 1 should exist", fieldStatsDay1)
    assertEquals("Count for day 1 should be 1", 1, fieldStatsDay1!!.count)
    assertEquals("Sum for day 1 should be 10.0", 10.0f, fieldStatsDay1.sum, 0.001f)

    // Save statistics for day 2
    val day2 = Date(2023, 1, 2, 1)
    statistics.withMockedCurrentDate(day2) {
      saveIfRegistered(EventPair(testField, 20))
    }

    val fieldStatsDay2 = statistics.state[day2]!!.values["test_field_across_days"]
    assertNotNull("Statistics for test_field_across_days on day 2 should exist", fieldStatsDay2)
    assertEquals("Count for day 2 should be 1", 1, fieldStatsDay2!!.count)
    assertEquals("Sum for day 2 should be 20.0", 20.0f, fieldStatsDay2.sum, 0.001f)
  }

  fun testDataPruning() {
    // Register a field for tracking
    val testField = EventFields.Int("test_field_pruning")
    LocalStatistics.Schema.register(testField)

    // Save statistics for an old date (more than 3 months ago)
    val oldDate = Date.now().minusMonths(4)
    statistics.withMockedCurrentDate(oldDate) {
      saveIfRegistered(EventPair(testField, 10))
    }

    // Save statistics for a recent date
    val recentDate = Date.now().minusMonths(1)
    statistics.withMockedCurrentDate(recentDate) {
      saveIfRegistered(EventPair(testField, 20))
    }

    // Save statistics for today and trigger pruning
    statistics.saveIfRegistered(EventPair(testField, 30))

    // Verify old data was pruned but recent data remains
    val state = statistics.state

    assertNull("Statistics for old date should be pruned", state[oldDate])

    val fieldStatsRecent = state[recentDate]!!.values["test_field_pruning"]
    assertNotNull("Statistics for test_field_pruning on recent date should exist", fieldStatsRecent)
    val fieldStatsToday = state[Date.now()]!!.values["test_field_pruning"]
    assertNotNull("Statistics for test_field_pruning today should exist", fieldStatsToday)
  }

  // Test for hourly statistics
  fun testHourlyStatistics() {
    val testField = EventFields.Int("test_hourly_field")
    LocalStatistics.Schema.register(testField)

    val baseDate = LocalDate.of(2023, 1, 1)
    val hour1Date = Date.of(baseDate, 10)
    val hour2Date = Date.of(baseDate, 15)

    statistics.withMockedCurrentDate(hour1Date) {
      saveIfRegistered(EventPair(testField, 100))
    }
    statistics.withMockedCurrentDate(hour2Date) {
      saveIfRegistered(EventPair(testField, 200))
    }

    // Verify we have separate stats for each hour
    val state = statistics.state

    val statsHour1 = state[hour1Date]
    assertNotNull("Statistics for hour1 should exist", statsHour1)
    assertEquals("Hour1 should have value 100", 100.0f, statsHour1!!.values["test_hourly_field"]!!.sum, 0.001f)

    val statsHour2 = state[hour2Date]
    assertNotNull("Statistics for hour2 should exist", statsHour2)
    assertEquals("Hour2 should have value 200", 200.0f, statsHour2!!.values["test_hourly_field"]!!.sum, 0.001f)
  }

  enum class TestEnum {
    VALUE1, VALUE2
  }
}
