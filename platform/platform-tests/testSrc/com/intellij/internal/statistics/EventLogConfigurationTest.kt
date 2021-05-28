// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.EventLogConfigOptionsService
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.eventLog.EventLogRecorderConfiguration
import com.intellij.internal.statistic.eventLog.validator.storage.EventLogMetadataLoader
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.*
import kotlin.test.assertNotEquals

class EventLogConfigurationTest : BasePlatformTestCase() {
  fun testMachineIdRegeneration() {
    doTestRegenerate({ it.machineIdConfiguration }, hashMapOf(EventLogConfigOptionsService.MACHINE_ID_SALT to "newSalt",
                                                              EventLogConfigOptionsService.MACHINE_ID_SALT_REVISION to "2"))
  }

  fun testNotRegenerateMachineIdForInitialValue() {
    doTestNotRegenerate({ it.machineIdConfiguration }, hashMapOf(EventLogConfigOptionsService.MACHINE_ID_SALT to "",
                                                              EventLogConfigOptionsService.MACHINE_ID_SALT_REVISION to "0"))
  }

  private fun doTestRegenerate(function: (configuration: EventLogRecorderConfiguration) -> Any, values: Map<String, String>) {
    val (initialValue, newValue) = doTest(function, values)
    assertNotEquals(initialValue, newValue)
  }

  private fun doTestNotRegenerate(function: (configuration: EventLogRecorderConfiguration) -> Any, values: Map<String, String>) {
    val (initialValue, newValue) = doTest(function, values)
    assertEquals(initialValue, newValue)
  }

  private fun doTest(function: (configuration: EventLogRecorderConfiguration) -> Any,
                     values: Map<String, String>): Pair<Any, Any> {
    val recorderId = "ABC"
    val configuration = EventLogConfiguration.getOrCreate(recorderId)
    val initialValue = function(configuration)

    EventLogConfigOptionsService.getInstance().updateOptions(recorderId, TestEventLogMetadataLoader(values))
    val newValue = function(configuration)
    return Pair(initialValue, newValue)
  }

  class TestEventLogMetadataLoader(private val values: Map<String, String>) : EventLogMetadataLoader {
    override fun getLastModifiedOnServer(): Long = 0

    override fun loadMetadataFromServer(): String = ""

    override fun getOptionValue(name: String): String? = values[name]
  }

}