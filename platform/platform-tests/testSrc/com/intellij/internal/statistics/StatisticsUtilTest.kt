// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.internal.statistic.utils.StatisticsUtil.getCurrentHourInUTC
import com.intellij.internal.statistic.utils.StatisticsUtil.getTimestampDateInUTC
import com.intellij.internal.statistic.utils.StatisticsUtil.roundLogarithmicTest
import com.intellij.internal.statistic.utils.StatisticsUtil.roundToHighestDigit
import com.intellij.internal.statistic.utils.StatisticsUtil.roundToPowerOfTwo
import com.intellij.internal.statistic.utils.StatisticsUtil.roundToUpperBoundInternalTest
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase
import org.assertj.core.api.Assertions
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
  fun `test round to power of two int`() {
    testRoundToPowerOfTwoInt(0, 0)
    testRoundToPowerOfTwoInt(-5, -8)
    testRoundToPowerOfTwoInt(5, 8)
    testRoundToPowerOfTwoInt(-1, -1)
    testRoundToPowerOfTwoInt(1, 1)
    testRoundToPowerOfTwoInt(-2, -2)
    testRoundToPowerOfTwoInt(2, 2)
    testRoundToPowerOfTwoInt(Int.MAX_VALUE, Int.MAX_VALUE)
    testRoundToPowerOfTwoInt(Int.MIN_VALUE, Int.MIN_VALUE)

    val minEdgeValue = -1073741824 // max value without overflow
    testRoundToPowerOfTwoInt(minEdgeValue, minEdgeValue)
    testRoundToPowerOfTwoInt(minEdgeValue - 1, Int.MIN_VALUE)
    testRoundToPowerOfTwoInt(minEdgeValue + 1, minEdgeValue)

    val maxEdgeValue = 1073741824 // min value without overflow
    testRoundToPowerOfTwoInt(maxEdgeValue, maxEdgeValue)
    testRoundToPowerOfTwoInt(maxEdgeValue - 1, maxEdgeValue)
    testRoundToPowerOfTwoInt(maxEdgeValue + 1, Int.MAX_VALUE)
  }

  private fun testRoundToPowerOfTwoInt(value: Int, expected: Int) {
    assertEquals(expected, roundToPowerOfTwo(value), "Incorrect key for value `$value`")
  }

  @Test
  fun `test round to power of two long`() {
    testRoundToPowerOfTwoLong(0, 0)
    testRoundToPowerOfTwoLong(-5, -8)
    testRoundToPowerOfTwoLong(5, 8)
    testRoundToPowerOfTwoLong(-1, -1)
    testRoundToPowerOfTwoLong(1, 1)
    testRoundToPowerOfTwoLong(-2, -2)
    testRoundToPowerOfTwoLong(2, 2)
    testRoundToPowerOfTwoLong(Long.MAX_VALUE, Long.MAX_VALUE)
    testRoundToPowerOfTwoLong(Long.MIN_VALUE, Long.MIN_VALUE)

    val minEdgeValue = -4611686018427387904 // max value without overflow
    testRoundToPowerOfTwoLong(minEdgeValue, minEdgeValue)
    testRoundToPowerOfTwoLong(minEdgeValue - 1, Long.MIN_VALUE)
    testRoundToPowerOfTwoLong(minEdgeValue + 1, minEdgeValue)

    val maxEdgeValue = 4611686018427387904 // min value without overflow
    testRoundToPowerOfTwoLong(maxEdgeValue, maxEdgeValue)
    testRoundToPowerOfTwoLong(maxEdgeValue - 1, maxEdgeValue)
    testRoundToPowerOfTwoLong(maxEdgeValue + 1, Long.MAX_VALUE)
  }

  private fun testRoundToPowerOfTwoLong(value: Long, expected: Long) {
    assertEquals(expected, roundToPowerOfTwo(value), "Incorrect key for value `$value`")
  }

  @Test
  fun `test round to highest digit int`() {
    // Test from -10..10
    for (i in -10..-5) {
      testRoundToHighestDigit(i, -10)
    }
    for (i in -4..-1) {
      testRoundToHighestDigit(i, -1)
    }
    testRoundToHighestDigit(0, 0)
    for (i in 1..4) {
      testRoundToHighestDigit(i, 1)
    }
    for (i in 5..10) {
      testRoundToHighestDigit(i, 10)
    }

    testRoundToHighestDigit(11, 10)
    testRoundToHighestDigit(16, 20)
    testRoundToHighestDigit(64, 60)
    testRoundToHighestDigit(65, 70)

    testRoundToHighestDigit(94, 90)
    testRoundToHighestDigit(95, 100)
    testRoundToHighestDigit(99, 100)

    for (i in 1..9) {
      val expected = i * 1000
      testRoundToHighestDigit(expected, expected)
    }

    testRoundToHighestDigit(1024, 1000)
    testRoundToHighestDigit(1500, 2000)
    testRoundToHighestDigit(1999, 2000)

    testRoundToHighestDigit(Int.MAX_VALUE, 2000000000)
    testRoundToHighestDigit(Int.MIN_VALUE + 1, -2000000000)
    testRoundToHighestDigit(Int.MIN_VALUE, -2000000000)
  }

  private fun testRoundToHighestDigit(value: Int, expected: Int) {
    assertEquals(expected, roundToHighestDigit(value), "Incorrect key for value `$value`")
  }

  @Test
  fun `test round to highest digit long`() {
    // Test from -10..10
    for (i in -10L..-5L) {
      testRoundToHighestDigit(i, -10L)
    }
    for (i in -4L..-1L) {
      testRoundToHighestDigit(i, -1L)
    }
    testRoundToHighestDigit(0L, 0L)
    for (i in 1L..4L) {
      testRoundToHighestDigit(i, 1L)
    }
    for (i in 5L..10L) {
      testRoundToHighestDigit(i, 10L)
    }

    testRoundToHighestDigit(11L, 10L)
    testRoundToHighestDigit(16L, 20L)
    testRoundToHighestDigit(64L, 60L)
    testRoundToHighestDigit(65L, 70L)

    testRoundToHighestDigit(94L, 90L)
    testRoundToHighestDigit(95L, 100L)
    testRoundToHighestDigit(99L, 100L)

    for (i in 1L..9L) {
      val expected = i * 1000L
      testRoundToHighestDigit(expected, expected)
    }

    testRoundToHighestDigit(1024L, 1000L)
    testRoundToHighestDigit(1500L, 2000L)
    testRoundToHighestDigit(1999L, 2000L)

    testRoundToHighestDigit(Long.MAX_VALUE, 9000000000000000000L)
    testRoundToHighestDigit(Long.MIN_VALUE + 1L, -9000000000000000000L)
    testRoundToHighestDigit(Long.MIN_VALUE, -9000000000000000000L)
  }

  private fun testRoundToHighestDigit(value: Long, expected: Long) {
    assertEquals(expected, roundToHighestDigit(value), "Incorrect key for value `$value`")
  }

  @Test
  fun `test round int to upper bound`() {
    // Test with bounds
    // on the edge
    testRoundToUpperBound(0, intArrayOf(0, 1, 2), 0)
    testRoundToUpperBound(1, intArrayOf(0, 1, 2), 1)
    testRoundToUpperBound(2, intArrayOf(0, 1, 2), 2)
    // between bounds
    testRoundToUpperBound(5, intArrayOf(1, 10, 100), 10)
    testRoundToUpperBound(50, intArrayOf(1, 10, 100), 100)
    // out of bounds
    testRoundToUpperBound(-1, intArrayOf(0, 1, 2), 0)
    testRoundToUpperBound(3, intArrayOf(0, 1, 2), 2)
    // corner cases
    testRoundToUpperBound(Int.MIN_VALUE, intArrayOf(0, 1, 2), 0)
    testRoundToUpperBound(Int.MAX_VALUE, intArrayOf(0, 1, 2), 2)
  }

  private fun testRoundToUpperBound(value: Int, bounds: IntArray, expected: Int) {
    assertEquals(expected, roundToUpperBoundInternalTest(value, bounds), "Incorrect key for value `$value`")
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

  @Test
  fun `test timestamp date in UTC`() {
    val dateInUTC = getTimestampDateInUTC(System.currentTimeMillis())
    TestCase.assertNotNull(dateInUTC)
    TestCase.assertEquals(6, dateInUTC.length)
  }

  @Test
  fun `test timestamp in UTC from unix time`() {
    TestCase.assertEquals("210218", getTimestampDateInUTC(1613689500000))
    TestCase.assertEquals("201020", getTimestampDateInUTC(1603238200000))
    TestCase.assertEquals("201019", getTimestampDateInUTC(1603132200000))
  }

  @Test
  fun `test custom timestamp in UTC`() {
    TestCase.assertEquals("200117", getTimestampDateInUTC(getCalendarByDate(2020, Calendar.JANUARY, 17, 10).timeInMillis))
    TestCase.assertEquals("190812", getTimestampDateInUTC(getCalendarByDate(2019, Calendar.AUGUST, 12, 10).timeInMillis))
    TestCase.assertEquals("210530", getTimestampDateInUTC(getCalendarByDate(2021, Calendar.MAY, 30, 10).timeInMillis))
    TestCase.assertEquals("180904", getTimestampDateInUTC(getCalendarByDate(2018, Calendar.SEPTEMBER, 4, 10).timeInMillis))
    TestCase.assertEquals("000701", getTimestampDateInUTC(getCalendarByDate(2000, Calendar.JULY, 1, 10).timeInMillis))
    TestCase.assertEquals("990209", getTimestampDateInUTC(getCalendarByDate(2099, Calendar.FEBRUARY, 9, 10).timeInMillis))
  }

  @Test
  fun `test custom timestamp from CET in UTC`() {
    val cet = ZoneOffset.ofHours(1)
    TestCase.assertEquals("200117", getTimestampDateInUTC(getCalendarByDate(2020, Calendar.JANUARY, 17, 10, cet).timeInMillis))
    TestCase.assertEquals("190812", getTimestampDateInUTC(getCalendarByDate(2019, Calendar.AUGUST, 12, 10, cet).timeInMillis))
    TestCase.assertEquals("210530", getTimestampDateInUTC(getCalendarByDate(2021, Calendar.MAY, 30, 10, cet).timeInMillis))
    TestCase.assertEquals("180904", getTimestampDateInUTC(getCalendarByDate(2018, Calendar.SEPTEMBER, 4, 10, cet).timeInMillis))
    TestCase.assertEquals("000701", getTimestampDateInUTC(getCalendarByDate(2000, Calendar.JULY, 1, 10, cet).timeInMillis))
    TestCase.assertEquals("990209", getTimestampDateInUTC(getCalendarByDate(2099, Calendar.FEBRUARY, 9, 10, cet).timeInMillis))
  }

  @Test
  fun `test custom timestamp from different time zones in UTC`() {
    TestCase.assertEquals("200117", getTimestampDateInUTC(getCalendarByDate(2020, Calendar.JANUARY, 17, 10, ZoneOffset.ofHours(1)).timeInMillis))
    TestCase.assertEquals("200117", getTimestampDateInUTC(getCalendarByDate(2020, Calendar.JANUARY, 17, 10, ZoneOffset.ofHours(-1)).timeInMillis))
    TestCase.assertEquals("200117", getTimestampDateInUTC(getCalendarByDate(2020, Calendar.JANUARY, 17, 10, ZoneOffset.ofHours(5)).timeInMillis))
    TestCase.assertEquals("200117", getTimestampDateInUTC(getCalendarByDate(2020, Calendar.JANUARY, 17, 10, ZoneOffset.ofHours(-8)).timeInMillis))
  }

  @Test
  fun `test timestamp out of range in UTC`() {
    TestCase.assertEquals("990629", getTimestampDateInUTC(getCalendarByDate(2100, Calendar.JUNE, 29, 10).timeInMillis))
    TestCase.assertEquals("990629", getTimestampDateInUTC(getCalendarByDate(2235, Calendar.JUNE, 29, 10).timeInMillis))
    TestCase.assertEquals("000629", getTimestampDateInUTC(getCalendarByDate(2000, Calendar.JUNE, 29, 10).timeInMillis))
    TestCase.assertEquals("000629", getTimestampDateInUTC(getCalendarByDate(1900, Calendar.JUNE, 29, 10).timeInMillis))
    TestCase.assertEquals("000629", getTimestampDateInUTC(getCalendarByDate(1999, Calendar.JUNE, 29, 10).timeInMillis))
  }

  @Test
  fun `test rounding duration`() {
    for (i in 0..49) assertRoundedValue(i, 0L)
    for (i in 50..99) assertRoundedValue(i, 50L)

    for (i in 100..199 step 3) assertRoundedValue(i, 100L)
    for (i in 300..399 step 7) assertRoundedValue(i, 300L)
    for (i in 25200..25299 step 9) assertRoundedValue(i, 25200L)
  }

  @Test
  fun `test rounding negative duration`() {
    for (i in -1 downTo -99 step 3) assertRoundedValue(i, 0L)
    for (i in -100 downTo -199 step 7) assertRoundedValue(i, -100L)
    for (i in -2345100 downTo -2345199 step 7) assertRoundedValue(i, -2345100L)
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

  private fun assertUsage(key: String, value: String, metric: MetricEvent, message: String? = null) {
    assertEquals(message, key, metric.eventId)
    assertEquals(message, value, metric.data.build()["value"])
  }

  private fun assertRoundedValue(raw: Int, expected: Long) {
    val rounded = StatisticsUtil.roundDuration(raw.toLong())
    assertEquals("Failed rounding for '$raw'", expected, rounded)
  }

  @Test
  fun roundLogarithmic_exact() {
    1.roundLogarithmicTest() mustBe 1
    2.roundLogarithmicTest() mustBe 2
    5.roundLogarithmicTest() mustBe 5
    1_000.roundLogarithmicTest() mustBe 1_000
    2_000_000_000.roundLogarithmicTest() mustBe 2_000_000_000
  }

  @Test
  fun roundLogarithmic_rounded() {
    4.roundLogarithmicTest() mustBe 5
    101.roundLogarithmicTest() mustBe 100
    123_456_789.roundLogarithmicTest() mustBe 100_000_000
    2_000_000_000.roundLogarithmicTest() mustBe 2_000_000_000
  }

  @Test
  fun roundLogarithmic_zero() {
    0.roundLogarithmicTest() mustBe 0
  }

  @Test
  fun roundLogarithmic_negative() {
    (-1).roundLogarithmicTest() mustBe -1
    Int.MIN_VALUE.roundLogarithmicTest() mustBe -2_000_000_000
  }

  @Test
  fun roundLogarithmic_huge() {
    2_000_000_001.roundLogarithmicTest() mustBe 2_000_000_000
    Int.MAX_VALUE.roundLogarithmicTest() mustBe 2_000_000_000
  }

  private infix fun <S> S?.mustBe(expected: S): S {
    Assertions.assertThat(this).isNotNull()
    this!!
    Assertions.assertThat(this).isEqualTo(expected)
    return this
  }
}