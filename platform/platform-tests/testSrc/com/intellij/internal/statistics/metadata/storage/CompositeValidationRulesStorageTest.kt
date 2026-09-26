// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.metadata.storage

import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.internal.statistic.eventLog.validator.storage.GroupValidationTestRule
import com.intellij.internal.statistic.eventLog.validator.storage.ValidationRulesInMemoryStorage
import com.intellij.internal.statistic.eventLog.validator.storage.ValidationTestRulesPersistedStorage
import org.assertj.core.api.Assertions.assertThat

internal class CompositeValidationRulesStorageTest : ValidationRulesBaseStorageTest() {

  override fun tearDown() {
    try {
      ValidationRulesInMemoryStorage.eventsValidators.clear()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testGetGroupRulesFromTest() {
    val storage = IntellijSensitiveDataValidator.getInstance(recorderId).validationRulesStorage
    ValidationTestRulesPersistedStorage.getTestStorage(recorderId, true)!!
      .addTestGroup(GroupValidationTestRule(
        groupId,
        true,
        "{\n" +
        "      \"event_id\" : [ \"{enum:RunSelectedBuild|RunTargetAction}\" ],\n" +
        "      \"event_data\" : {\n" +
        "        \"context_menu\" : [ \"{enum#boolean}\" ]\n" +
        "      }\n" +
        "    }"
      ))

    val groupRules = storage.getGroupValidators(groupId).eventGroupRules
    assertThat(groupRules).isNotNull
    assertThat(groupRules!!.getEventDataRules()).isNotEmpty
  }
}