// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.internal.statistic.utils.StatisticsUtil.getCurrentHourInUTC
import com.intellij.internal.statistic.utils.StatisticsUtil.getNextPowerOfTwo
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.ZoneOffset
import java.util.*
import kotlin.test.assertEquals

@Suppress("SameParameterValue")
@RunWith(JUnit4::class)
class StatisticsUtilTest : LightPlatformTestCase() {
  @Test
  fun test_counting_usage() {
    val steps = listOf(0, 1, 2, 10, 1000, 10 * 1000, 1000 * 1000)
    assertCountingUsage("test.value.count", "0", 0, steps)
    assertCountingUsage("test.value.count", "1", 1, steps)
    assertCountingUsage("test.value.count", "2+", 2, steps)
    assertCountingUsage("test.value.count", "2+", 3, steps)
    assertCountingUsage("test.value.count", "2+", 9, steps)
    assertCountingUsage("test.value.count", "10+", 10, steps)
    assertCountingUsage("test.value.count", "10+", 500, steps)
    assertCountingUsage("test.value.count", "1K+", 1000, steps)
    assertCountingUsage("test.value.count", "10K+", 200 * 1000, steps)
    assertCountingUsage("test.value.count", "10K+", 999999, steps)
    assertCountingUsage("test.value.count", "1M+", 1000 * 1000, steps)
    assertCountingUsage("test.value.count", "1M+", 2000 * 1000, steps)
  }

  @Test
  fun `test counting usage on empty list`() {
    val emptySteps = listOf<Int>()
    assertCountingUsage("test.value.count", "1", 1, emptySteps)
  }

  @Test
  fun `test counting usage if value is less than the first step`() {
    val steps = listOf(1, 5, 10)
    assertCountingUsage("test.value.count", "<1", 0, steps)
  }

  @Test
  fun `test next power of two`() {
    testPowerOfTwo(0, 1)
    testPowerOfTwo(-5, 1)
    testPowerOfTwo(1, 1)
    testPowerOfTwo(2, 2)
    testPowerOfTwo(3, 4)
    testPowerOfTwo(5, 8)
  }

  private fun testPowerOfTwo(value: Int, expected: Int) {
    assertEquals(expected, getNextPowerOfTwo(value), "Incorrect key for value `$value`")
  }

  @Test
  fun `test hash sensitive data`() {
    val salt = byteArrayOf(45, 105, 19, -80, 109, 38, 24, -23, 27, -102, -123, 92, 60, -63, -83, -67, -66, -17, -26, 44, 123, 28, 40, -74, 77, -105, 105, -41, 36, -55, -21, 5)
    doTestHashing(salt, "test-project-name", "dfa488a68d19d909af416ea02c8013e314562803d421ae747d7fec06dd080609")
    doTestHashing(salt, "SomeFramework", "894bcfb4eb52e802ce750112ad3ed6d16f049c63f385f5a0d6c6ab5d63e54c4e")
    doTestHashing(salt, "project", "4d85bff7bbefd0d5695450874de9b38fb1f10bacadd23abdd4ea511248aab7f0")
  }

  @Test
  fun `test current date in UTC`() {
    val dateInUTC = getCurrentHourInUTC()
    TestCase.assertNotNull(dateInUTC)
    TestCase.assertEquals(8, dateInUTC.length)
  }

  @Test
  fun `test date in UTC from unix time`() {
    TestCase.assertEquals("21021823", getCurrentHourInUTC(getCalendarByUnixTime(1613689500000)))
    TestCase.assertEquals("20102023", getCurrentHourInUTC(getCalendarByUnixTime(1603238200000)))
    TestCase.assertEquals("20101918", getCurrentHourInUTC(getCalendarByUnixTime(1603132200000)))
  }

  @Test
  fun `test custom date in UTC`() {
    TestCase.assertEquals("20011710", getCurrentHourInUTC(getCalendarByDate(2020, Calendar.JANUARY, 17, 10)))
    TestCase.assertEquals("19081210", getCurrentHourInUTC(getCalendarByDate(2019, Calendar.AUGUST, 12, 10)))
    TestCase.assertEquals("21053010", getCurrentHourInUTC(getCalendarByDate(2021, Calendar.MAY, 30, 10)))
    TestCase.assertEquals("18090410", getCurrentHourInUTC(getCalendarByDate(2018, Calendar.SEPTEMBER, 4, 10)))
    TestCase.assertEquals("00070110", getCurrentHourInUTC(getCalendarByDate(2000, Calendar.JULY, 1, 10)))
    TestCase.assertEquals("99020910", getCurrentHourInUTC(getCalendarByDate(2099, Calendar.FEBRUARY, 9, 10)))
  }

  @Test
  fun `test custom date from CET in UTC`() {
    val cet = ZoneOffset.ofHours(1)
    TestCase.assertEquals("20011709", getCurrentHourInUTC(getCalendarByDate(2020, Calendar.JANUARY, 17, 10, cet)))
    TestCase.assertEquals("19081209", getCurrentHourInUTC(getCalendarByDate(2019, Calendar.AUGUST, 12, 10, cet)))
    TestCase.assertEquals("21053009", getCurrentHourInUTC(getCalendarByDate(2021, Calendar.MAY, 30, 10, cet)))
    TestCase.assertEquals("18090409", getCurrentHourInUTC(getCalendarByDate(2018, Calendar.SEPTEMBER, 4, 10, cet)))
    TestCase.assertEquals("00070109", getCurrentHourInUTC(getCalendarByDate(2000, Calendar.JULY, 1, 10, cet)))
    TestCase.assertEquals("99020909", getCurrentHourInUTC(getCalendarByDate(2099, Calendar.FEBRUARY, 9, 10, cet)))
  }

  @Test
  fun `test custom date from different time zones in UTC`() {
    TestCase.assertEquals("20011709", getCurrentHourInUTC(getCalendarByDate(2020, Calendar.JANUARY, 17, 10, ZoneOffset.ofHours(1))))
    TestCase.assertEquals("20011711", getCurrentHourInUTC(getCalendarByDate(2020, Calendar.JANUARY, 17, 10, ZoneOffset.ofHours(-1))))
    TestCase.assertEquals("20011705", getCurrentHourInUTC(getCalendarByDate(2020, Calendar.JANUARY, 17, 10, ZoneOffset.ofHours(5))))
    TestCase.assertEquals("20011718", getCurrentHourInUTC(getCalendarByDate(2020, Calendar.JANUARY, 17, 10, ZoneOffset.ofHours(-8))))
  }

  @Test
  fun `test date out of range in UTC`() {
    TestCase.assertEquals("99062910", getCurrentHourInUTC(getCalendarByDate(2100, Calendar.JUNE, 29, 10)))
    TestCase.assertEquals("99062910", getCurrentHourInUTC(getCalendarByDate(2235, Calendar.JUNE, 29, 10)))
    TestCase.assertEquals("00062910", getCurrentHourInUTC(getCalendarByDate(2000, Calendar.JUNE, 29, 10)))
    TestCase.assertEquals("00062910", getCurrentHourInUTC(getCalendarByDate(1900, Calendar.JUNE, 29, 10)))
    TestCase.assertEquals("00062910", getCurrentHourInUTC(getCalendarByDate(1999, Calendar.JUNE, 29, 10)))
  }

  private fun getCalendarByDate(year: Int, month: Int, day: Int, hour: Int, zone: ZoneOffset = ZoneOffset.UTC): Calendar {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone(zone), Locale.ENGLISH)
    calendar.set(year, month, day, hour, 15)
    return calendar
  }

  private fun getCalendarByUnixTime(timeMs: Long): Calendar {
    val calendar = Calendar.getInstance(Locale.ENGLISH)
    calendar.timeInMillis = timeMs
    return calendar
  }

  private fun doTestHashing(salt: ByteArray, data: String, expected: String) {
    val actual = EventLogConfiguration.hashSha256(salt, data)
    assertEquals("Hashing algorithm was changed for '$data'", expected, actual)
  }

  private fun assertCountingUsage(expectedKey: String, expectedValue: String, actualValue: Int, steps: List<Int>) {
    val metric = getCountingUsage("test.value.count", actualValue, steps)
    assertUsage(expectedKey, expectedValue, metric, "Incorrect key for value '$actualValue'")
  }

  private fun assertUsage(key: String, value: String, metric: MetricEvent, message: String? = null) {
    assertEquals(message, key, metric.eventId)
    assertEquals(message, value, metric.data.build()["value"])
  }

  private fun getCountingUsage(key: String, value: Int, steps: List<Int>) : MetricEvent {
    return newMetric(key, StatisticsUtil.getCountingStepName(value, steps))
  }
}