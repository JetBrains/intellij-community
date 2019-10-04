// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.whitelist


import com.intellij.internal.statistic.eventLog.whitelist.WhitelistTestGroupStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

internal class WhitelistTestGroupStorageTest : WhitelistBaseStorageTest() {

  @Test
  fun testAddWhitelistGroup() {
    val storageForTest = WhitelistTestGroupStorage.getInstance(recorderId)
    storageForTest.addTestGroup(groupId)

    val groupRules = storageForTest.getGroupRules(groupId)
    assertThat(groupRules).isNotNull
    assertThat(groupRules!!.eventDataRules).isEmpty()
  }

  @Test
  fun testAddGroupWithCustomRules() {
    val storageForTest = WhitelistTestGroupStorage.getInstance(recorderId)
    storageForTest.addGroupWithCustomRules(
      groupId,
      "{\n" +
      "      \"event_id\" : [ \"{enum:RunSelectedBuild|RunTargetAction}\" ],\n" +
      "      \"event_data\" : {\n" +
      "        \"context_menu\" : [ \"{enum#boolean}\" ]\n" +
      "      }\n" +
      "    }"
    )
    val groupRules = storageForTest.getGroupRules(groupId)
    assertThat(groupRules).isNotNull
    assertThat(groupRules!!.eventDataRules).isNotEmpty
  }

  fun testCleanup() {
    val storageForTest1 = WhitelistTestGroupStorage.getInstance(recorderId)
    storageForTest1.addTestGroup(groupId)
    val storageForTest2 = WhitelistTestGroupStorage.getInstance(secondRecorderId)
    storageForTest2.addTestGroup(groupId)

    WhitelistTestGroupStorage.cleanupAll()
    assertThat(storageForTest1.getGroupRules(groupId)).isNull()
    assertThat(storageForTest2.getGroupRules(groupId)).isNull()
  }
}