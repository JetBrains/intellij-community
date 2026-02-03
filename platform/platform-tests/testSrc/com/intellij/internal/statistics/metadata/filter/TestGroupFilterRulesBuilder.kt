// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.metadata.filter

import com.intellij.internal.statistic.eventLog.EventLogBuild
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupFilterRules
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupFilterRules.*
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupsFilterRules

class TestGroupFilterRulesBuilder {
  private val groupIds: MutableSet<String> = HashSet()
  private val groupVersions: MutableMap<String, MutableList<VersionRange>> = HashMap()
  private val groupBuilds: MutableMap<String, MutableList<BuildRange<EventLogBuild>>> = HashMap()

  fun addVersion(id: String, from: Int, to: Int): TestGroupFilterRulesBuilder {
    if (!groupVersions.containsKey(id)) {
      groupIds.add(id)
      groupVersions[id] = mutableListOf()
    }
    groupVersions[id]!!.add(VersionRange(from, to))
    return this
  }

  fun addVersion(id: String, from: String?, to: String?): TestGroupFilterRulesBuilder {
    if (!groupVersions.containsKey(id)) {
      groupIds.add(id)
      groupVersions[id] = mutableListOf()
    }
    groupVersions[id]!!.add(VersionRange.create(from, to))
    return this
  }

  fun addBuild(id: String, from: EventLogBuild?, to: EventLogBuild?): TestGroupFilterRulesBuilder {
    if (!groupBuilds.containsKey(id)) {
      groupIds.add(id)
      groupBuilds[id] = mutableListOf()
    }
    groupBuilds[id]!!.add(BuildRange(from, to))
    return this
  }

  fun addBuild(id: String, from: String?, to: String?): TestGroupFilterRulesBuilder {
    if (!groupBuilds.containsKey(id)) {
      groupIds.add(id)
      groupBuilds[id] = mutableListOf()
    }
    groupBuilds[id]!!.add(BuildRange.create(from, to, EventLogBuild.EVENT_LOG_BUILD_PRODUCER))
    return this
  }

  fun addGroup(id: String): TestGroupFilterRulesBuilder {
    groupIds.add(id)
    return this
  }

  fun build(): EventGroupsFilterRules<EventLogBuild> {
    val result = HashMap<String, EventGroupFilterRules<EventLogBuild>>()
    for (groupId in groupIds) {
      val builds: List<BuildRange<EventLogBuild>> = groupBuilds.getOrDefault(groupId, emptyList())
      val versions: List<VersionRange> = groupVersions.getOrDefault(groupId, emptyList())
      result[groupId] = EventGroupFilterRules(builds, versions)
    }
    return EventGroupsFilterRules.create(result, EventLogBuild.EVENT_LOG_BUILD_PRODUCER)
  }
}