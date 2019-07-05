// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.whitelist


import com.intellij.internal.statistic.eventLog.whitelist.WhitelistStorageForTest
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

internal class WhiteListStorageForTestTest : BasePlatformTestCase() {
  private val groupId = "test.group"
  private val recorderId = "FUS"

  override fun tearDown() {
    super.tearDown()
    WhitelistStorageForTest.cleanupAll()
  }

  @Test
  fun testAddWhitelistGroup() {

    val storageForTest = WhitelistStorageForTest.getInstance(recorderId)
    storageForTest.addTestGroup(groupId)

    val groupRules = storageForTest.getGroupRules(groupId)
    assertThat(groupRules).isNotNull
    assertThat(groupRules!!.eventDataRules).isEmpty()
  }

  @Test
  fun testAddGroupWithCustomRules() {

    val storageForTest = WhitelistStorageForTest.getInstance(recorderId)
    storageForTest.addGroupWithCustomRules(groupId, "{\n" +
                                                    "      \"event_id\" : [ \"{enum:RunSelectedBuild|RunTargetAction}\" ],\n" +
                                                    "      \"event_data\" : {\n" +
                                                    "        \"context_menu\" : [ \"{enum#boolean}\" ]\n" +
                                                    "      }\n" +
                                                    "    }")
    val groupRules = storageForTest.getGroupRules(groupId)
    assertThat(groupRules).isNotNull
    assertThat(groupRules!!.eventDataRules).isNotEmpty
  }

  fun testCleanup() {
    val storageForTest1 = WhitelistStorageForTest.getInstance(recorderId)
    storageForTest1.addTestGroup(groupId)
    val storageForTest2 = WhitelistStorageForTest.getInstance("TEST")
    storageForTest2.addTestGroup(groupId)

    WhitelistStorageForTest.cleanupAll()
    assertThat(storageForTest1.getGroupRules(groupId)).isNull()
    assertThat(storageForTest2.getGroupRules(groupId)).isNull()
  }
}