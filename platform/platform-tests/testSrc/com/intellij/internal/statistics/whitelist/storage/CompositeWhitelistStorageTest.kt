// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.whitelist.storage

import com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules
import com.intellij.internal.statistic.eventLog.whitelist.InMemoryWhitelistStorage
import com.intellij.internal.statistic.eventLog.whitelist.LocalWhitelistGroup
import com.intellij.internal.statistic.eventLog.whitelist.WhitelistTestGroupStorage
import org.assertj.core.api.Assertions.assertThat

internal class CompositeWhitelistStorageTest : WhitelistBaseStorageTest() {

  override fun tearDown() {
    super.tearDown()
    InMemoryWhitelistStorage.eventsValidators.clear()
  }

  fun testGetGroupRulesFromTest() {
    val storage = SensitiveDataValidator.getInstance(recorderId).whiteListStorage
    WhitelistTestGroupStorage.getTestStorage(recorderId)!!
      .addTestGroup(LocalWhitelistGroup(
        groupId,
        true,
        "{\n" +
        "      \"event_id\" : [ \"{enum:RunSelectedBuild|RunTargetAction}\" ],\n" +
        "      \"event_data\" : {\n" +
        "        \"context_menu\" : [ \"{enum#boolean}\" ]\n" +
        "      }\n" +
        "    }"
      ))
    InMemoryWhitelistStorage.eventsValidators[groupId] = EventGroupRules.EMPTY

    val groupRules = storage.getGroupRules(groupId)
    assertThat(groupRules).isNotNull
    assertThat(groupRules!!.eventDataRules).isNotEmpty
  }

  fun testGetGroupRules() {
    val mergedStorage = SensitiveDataValidator.getInstance(recorderId).whiteListStorage
    InMemoryWhitelistStorage.eventsValidators[groupId] = EventGroupRules.EMPTY

    val groupRules = mergedStorage.getGroupRules(groupId)
    assertThat(groupRules).isNotNull
    assertThat(groupRules!!.eventDataRules).isEmpty()
  }
}