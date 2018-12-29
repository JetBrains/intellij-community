// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.UserIdManager
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class UserIdManagerTest {
  @Test
  fun testUserIdHasConstantPostfixLength() {
    val id = UserIdManager.calculateId(Calendar.getInstance(), '2')
    assertEquals(36, id.substring(7).length)
  }

  @Test
  fun testUserIdDatePrefix() {
    val calendar = Calendar.getInstance()
    calendar.set(2017, 4, 15)
    val id = UserIdManager.calculateId(calendar, '2')
    assertEquals("150517", id.substring(0, 6))
  }

  @Test
  fun testUserIdDatePrefixDateBelowLimit() {
    val calendar = Calendar.getInstance()
    calendar.set(1970, 4, 15)
    val id = UserIdManager.calculateId(calendar, '2')
    assertEquals("150500", id.substring(0, 6))
  }

  @Test
  fun testUserIdDatePrefixDateAboveLimit() {
    val calendar = Calendar.getInstance()
    calendar.set(2120, 4, 15)
    val id = UserIdManager.calculateId(calendar, '2')
    assertEquals("150599", id.substring(0, 6))
  }

  @Test
  fun testUserIdOsCharacter() {
    val calendar = Calendar.getInstance()
    calendar.set(2017, 4, 15)
    val id = UserIdManager.calculateId(calendar, '2')
    assertEquals('2', id[6])
  }
}