/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.text

import com.intellij.openapi.util.Clock
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class DateFormatUtilTest {
  @Before fun setUp() {
    Clock.reset()
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