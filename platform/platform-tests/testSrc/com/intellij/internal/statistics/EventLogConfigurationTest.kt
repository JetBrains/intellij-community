// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.config.EventLogOptions.MACHINE_ID_SALT
import com.intellij.internal.statistic.config.EventLogOptions.MACHINE_ID_SALT_REVISION
import com.intellij.internal.statistic.eventLog.EventLogConfigOptionsService
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.eventLog.EventLogRecorderConfiguration
import com.intellij.internal.statistic.eventLog.validator.storage.EventLogMetadataLoader
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.test.assertNotEquals

class EventLogConfigurationTest : BasePlatformTestCase() {

  // Test that two different recorders can have same machine/device id if the alternative recorder id is provided
  fun testMachineIdDeviceIdAlternativeRecorder() {
    val configuration = EventLogConfiguration.getInstance().getOrCreate("ABC", null)
    val alternativeConfiguration = EventLogConfiguration.getInstance().getOrCreate("DEF", "ABC")

    assertNotNull(configuration.deviceId)
    assertNotNull(configuration.machineId)

    assertEquals(configuration.deviceId, alternativeConfiguration.deviceId)
    assertEquals(configuration.machineId, alternativeConfiguration.machineId)
  }

  fun testMachineIdRegeneration() {
    doTestRegenerate({ it.machineId }, hashMapOf(MACHINE_ID_SALT to "newSalt",
                                                 MACHINE_ID_SALT_REVISION to "2"))
  }

  fun testNotRegenerateMachineIdForInitialValue() {
    doTestNotRegenerate({ it.machineId }, hashMapOf(MACHINE_ID_SALT to "",
                                                    MACHINE_ID_SALT_REVISION to "0"))
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
    val configuration = EventLogConfiguration.getInstance().getOrCreate(recorderId)
    val initialValue = function(configuration)

    EventLogConfigOptionsService.getInstance().updateOptions(recorderId, TestEventLogMetadataLoader(values))
    val newValue = function(configuration)
    return Pair(initialValue, newValue)
  }

  class TestEventLogMetadataLoader(private val values: Map<String, String>) : EventLogMetadataLoader {
    override fun getLastModifiedOnServer(): Long = 0

    override fun loadMetadataFromServer(): String = ""

    override fun getOptionValues(): Map<String, String> = values
  }
}