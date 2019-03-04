// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.DeviceIdManager
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DeviceIdManagerTest {

  @Test
  fun testDeviceIdHasConstantPostfixLength() {
    val id = DeviceIdManager.generateId(Calendar.getInstance(), '2')
    assertEquals(36, id.substring(7).length)
  }

  @Test
  fun testDeviceIdDatePrefix() {
    val calendar = Calendar.getInstance()
    calendar.set(2017, Calendar.MAY, 15)
    val id = DeviceIdManager.generateId(calendar, '2')
    assertEquals("150517", id.substring(0, 6))
  }

  @Test
  fun testDeviceIdDatePrefixLowestPossible() {
    val calendar = Calendar.getInstance()
    calendar.set(2000, Calendar.APRIL, 15)
    val id = DeviceIdManager.generateId(calendar, '2')
    assertEquals("150400", id.substring(0, 6))
  }

  @Test
  fun testDeviceIdDatePrefixDateBelowLimit() {
    val calendar = Calendar.getInstance()
    calendar.set(1970, Calendar.JANUARY, 15)
    val id = DeviceIdManager.generateId(calendar, '2')
    assertEquals("150100", id.substring(0, 6))
  }

  @Test
  fun testDeviceIdDatePrefixDateMaximum() {
    val calendar = Calendar.getInstance()
    calendar.set(2099, Calendar.DECEMBER, 15)
    val id = DeviceIdManager.generateId(calendar, '2')
    assertEquals("151299", id.substring(0, 6))
  }

  @Test
  fun testDeviceIdDatePrefixDateAboveLimit() {
    val calendar = Calendar.getInstance()
    calendar.set(2120, Calendar.JULY, 15)
    val id = DeviceIdManager.generateId(calendar, '2')
    assertEquals("150799", id.substring(0, 6))
  }

  @Test
  fun testDeviceIdOsCharacter() {
    val calendar = Calendar.getInstance()
    calendar.set(2017, Calendar.JUNE, 15)
    val id = DeviceIdManager.generateId(calendar, '2')
    assertEquals('2', id[6])
  }

  @Test
  fun testDeviceIdCalendarDoesnChange() {
    val calendar = Calendar.getInstance()
    calendar.set(2123, 0, 1)

    DeviceIdManager.generateId(calendar, '2')
    val usedDate = calendar.get(Calendar.YEAR).toString() + "/" + calendar.get(Calendar.MONTH) + "/" + calendar.get(Calendar.DAY_OF_MONTH)

    val currentDate = Calendar.getInstance()
    val newData = currentDate.get(Calendar.YEAR).toString() + "/" + currentDate.get(Calendar.MONTH) + "/" + currentDate.get(Calendar.DAY_OF_MONTH)
    assertNotEquals(usedDate, newData)
  }
}