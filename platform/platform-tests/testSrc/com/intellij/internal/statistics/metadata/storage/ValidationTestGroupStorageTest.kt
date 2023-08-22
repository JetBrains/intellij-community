// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.metadata.storage


import com.intellij.internal.statistic.eventLog.validator.storage.GroupValidationTestRule
import com.intellij.internal.statistic.eventLog.validator.storage.ValidationTestRulesPersistedStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

internal class ValidationTestGroupStorageTest : ValidationRulesBaseStorageTest() {

  @Test
  fun testAddGroupValidationRules() {
    val storageForTest = ValidationTestRulesPersistedStorage.getTestStorage(recorderId, true)!!
    storageForTest.addTestGroup(GroupValidationTestRule(groupId, false))

    val groupRules = storageForTest.getGroupRules(groupId)
    assertThat(groupRules).isNotNull
    assertThat(groupRules!!.eventDataRules).isEmpty()
  }

  @Test
  fun testAddGroupWithCustomRules() {
    val storageForTest = ValidationTestRulesPersistedStorage.getTestStorage(recorderId, true)!!
    storageForTest.addTestGroup(GroupValidationTestRule(
      groupId,
      true,
      "{\n" +
      "      \"event_id\" : [ \"{enum:RunSelectedBuild|RunTargetAction}\" ],\n" +
      "      \"event_data\" : {\n" +
      "        \"context_menu\" : [ \"{enum#boolean}\" ]\n" +
      "      }\n" +
      "    }"
    ))
    val groupRules = storageForTest.getGroupRules(groupId)
    assertThat(groupRules).isNotNull
    assertThat(groupRules!!.eventDataRules).isNotEmpty
  }

  fun testCleanup() {
    val storageForTest1 = ValidationTestRulesPersistedStorage.getTestStorage(recorderId, true)!!
    storageForTest1.addTestGroup(GroupValidationTestRule(groupId, false))
    val storageForTest2 = ValidationTestRulesPersistedStorage.getTestStorage(secondRecorderId, true)!!
    storageForTest2.addTestGroup(GroupValidationTestRule(groupId, false))

    ValidationTestRulesPersistedStorage.cleanupAll(listOf(recorderId, secondRecorderId))
    assertThat(storageForTest1.getGroupRules(groupId)).isNull()
    assertThat(storageForTest2.getGroupRules(groupId)).isNull()
  }
}