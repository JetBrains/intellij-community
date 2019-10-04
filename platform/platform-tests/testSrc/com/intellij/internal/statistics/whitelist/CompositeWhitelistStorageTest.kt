// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.whitelist

import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules
import com.intellij.internal.statistic.eventLog.whitelist.InMemoryWhitelistStorage
import com.intellij.internal.statistic.eventLog.whitelist.WhitelistStorageProvider
import com.intellij.internal.statistic.eventLog.whitelist.WhitelistTestGroupStorage
import org.assertj.core.api.Assertions.assertThat

internal class CompositeWhitelistStorageTest : WhitelistBaseStorageTest() {

  override fun tearDown() {
    super.tearDown()
    InMemoryWhitelistStorage.eventsValidators.clear()
  }

  fun testGetGroupRulesFromTest() {
    val mergedStorage = WhitelistStorageProvider.getInstance(recorderId)
    WhitelistTestGroupStorage.getInstance(recorderId)
      .addGroupWithCustomRules(
        groupId,
        "{\n" +
        "      \"event_id\" : [ \"{enum:RunSelectedBuild|RunTargetAction}\" ],\n" +
        "      \"event_data\" : {\n" +
        "        \"context_menu\" : [ \"{enum#boolean}\" ]\n" +
        "      }\n" +
        "    }"
      )
    InMemoryWhitelistStorage.eventsValidators[groupId] = WhiteListGroupRules.EMPTY

    val groupRules = mergedStorage.getGroupRules(groupId)
    assertThat(groupRules).isNotNull
    assertThat(groupRules!!.eventDataRules).isNotEmpty
  }

  fun testGetGroupRules() {
    val mergedStorage = WhitelistStorageProvider.getInstance(recorderId)
    InMemoryWhitelistStorage.eventsValidators[groupId] = WhiteListGroupRules.EMPTY

    val groupRules = mergedStorage.getGroupRules(groupId)
    assertThat(groupRules).isNotNull
    assertThat(groupRules!!.eventDataRules).isEmpty()
  }
}