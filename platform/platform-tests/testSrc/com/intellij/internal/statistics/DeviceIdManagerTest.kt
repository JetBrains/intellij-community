// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.DeviceIdManager
import org.junit.Test
import java.time.LocalDate
import java.time.Month
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DeviceIdManagerTest {
  @Test
  fun testDeviceIdHasConstantPostfixLength() {
    val id = DeviceIdManager.generateId(LocalDate.now(), '2')
    assertEquals(36, id.substring(7).length)
  }

  @Test
  fun testDeviceIdDatePrefix() {
    val id = DeviceIdManager.generateId(LocalDate.of(2017, Month.MAY, 15), '2')
    assertEquals("150517", id.substring(0, 6))
  }

  @Test
  fun testDeviceIdDatePrefixLowestPossible() {
    val id = DeviceIdManager.generateId(LocalDate.of(2000, Month.APRIL, 15), '2')
    assertEquals("150400", id.substring(0, 6))
  }

  @Test
  fun testDeviceIdWithCustomFormatLocale() {
    val formatLocale = Locale.getDefault(Locale.Category.FORMAT)
    try {
      val locale = Locale("th", "TH", "TH")
      Locale.setDefault(Locale.Category.FORMAT, locale)
      val id = DeviceIdManager.generateId(LocalDate.of(2000, Month.APRIL, 15), '2')
      assertEquals("150400", id.substring(0, 6))
    }
    finally {
      Locale.setDefault(Locale.Category.FORMAT, formatLocale)
    }
  }

  @Test
  fun testDeviceIdDatePrefixDateBelowLimit() {
    val id = DeviceIdManager.generateId(LocalDate.of(1970, Month.JANUARY, 15), '2')
    assertEquals("150100", id.substring(0, 6))
  }

  @Test
  fun testDeviceIdDatePrefixDateMaximum() {
    val id = DeviceIdManager.generateId(LocalDate.of(2099, Month.DECEMBER, 15), '2')
    assertEquals("151299", id.substring(0, 6))
  }

  @Test
  fun testDeviceIdDatePrefixDateAboveLimit() {
    val id = DeviceIdManager.generateId(LocalDate.of(2120, Month.JULY, 15), '2')
    assertEquals("150799", id.substring(0, 6))
  }

  @Test
  fun testDeviceIdOsCharacter() {
    val id = DeviceIdManager.generateId(LocalDate.of(2017, Month.JUNE, 15), '2')
    assertEquals('2', id[6])
  }

  @Test
  fun testDeviceIdCurrentDateDoesNotChange() {
    val usedDate = LocalDate.of(2123, Month.JANUARY, 1)
    DeviceIdManager.generateId(usedDate, '2')
    val currentDate = LocalDate.now()
    assertNotEquals(usedDate, currentDate)
  }
}
