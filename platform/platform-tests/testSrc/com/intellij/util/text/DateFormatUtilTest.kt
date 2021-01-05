// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.util.Clock
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.IoTestUtil.assumeMacOS
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class DateFormatUtilTest {
  @Before fun setUp() {
    Clock.reset()
  }

  @Test fun system() {
    assumeMacOS()
    val testDate = LocalDateTime.of(2019, 5, 22, 13, 45).toMillis()
    val helper = File(DateFormatUtilTest::class.java.getResource("DateFormatUtilTest_macOS")!!.toURI())
    FileUtil.setExecutable(helper)
    val expected = ExecUtil.execAndGetOutput(GeneralCommandLine(helper.path, "${testDate / 1000}")).stdoutLines[0]
    assertEquals(expected, DateFormatUtil.formatDateTime(testDate))
  }

  @Test fun prettyDate() {
    val now = LocalDateTime.now()
    assertPrettyDate("Today", now)
    assertPrettyDate("Today", now.truncatedTo(ChronoUnit.DAYS))
    assertPrettyDate("Yesterday", now.minus(Duration.ofDays(1)))

    val d1 = LocalDateTime.parse("2003-12-10T17:00:00")
    assertPrettyDate(DateFormatUtil.formatDate(d1.toMillis()), d1)
    val d2 = LocalDateTime.parse("2004-12-08T23:59:59")
    assertPrettyDate(DateFormatUtil.formatDate(d2.toMillis()), d2)
  }

  @Test fun prettyDateTime() {
    val now = LocalDateTime.now()
    assertPrettyDateTime("Moments ago", now.minus(Duration.ofSeconds(1)))
    assertPrettyDateTime("A minute ago", now.minus(Duration.ofMinutes(1)))
    assertPrettyDateTime("5 minutes ago", now.minus(Duration.ofMinutes(5)))
    assertPrettyDateTime("1 hour ago", now.minus(Duration.ofHours(1)))

    val d1 = now.plus(Duration.ofMinutes(65))
    if (d1.dayOfYear == now.dayOfYear) {
      assertPrettyDateTime("Today " + DateFormatUtil.formatTime(d1.toMillis()), d1)
    }
    val d2 = now.minus(Duration.ofMinutes(65))
    if (d2.dayOfYear == now.dayOfYear) {
      assertPrettyDateTime("Today " + DateFormatUtil.formatTime(d2.toMillis()), d2)
    }
    val d3 = now.minus(Duration.ofDays(1))
    assertPrettyDateTime("Yesterday " + DateFormatUtil.formatTime(d3.toMillis()), d3)
  }

  @Test fun time() {
    val t = LocalDateTime.parse("1970-01-01T09:28:15")
    assertThat(DateFormatUtil.formatTime(t.toMillis())).contains("9").contains("28").doesNotContain("15")
    assertThat(DateFormatUtil.formatTimeWithSeconds(t.toMillis())).contains("9").contains("28").contains("15")
  }

  @Test fun aboutDialogDate() {
    assertEquals("January 1, 1999", DateFormatUtil.formatAboutDialogDate(LocalDateTime.parse("1999-01-01T00:00:00").toMillis()))
    assertEquals("December 12, 2012", DateFormatUtil.formatAboutDialogDate(LocalDateTime.parse("2012-12-12T15:35:12").toMillis()))
  }

  @Test fun frequency() {
    assertEquals("Once in 2 minutes", DateFormatUtil.formatFrequency(2L * 60 * 1000))
    assertEquals("Once in a few moments", DateFormatUtil.formatFrequency(1000L))
  }

  private fun assertPrettyDate(expected: String, now: LocalDateTime) {
    assertEquals(expected, DateFormatUtil.formatPrettyDate(now.toMillis()))
  }

  private fun assertPrettyDateTime(expected: String, now: LocalDateTime) {
    assertEquals(expected, DateFormatUtil.formatPrettyDateTime(now.toMillis()))
  }

  private fun LocalDateTime.toMillis() = atZone(ZoneId.systemDefault()).toEpochSecond() * 1000L
}
