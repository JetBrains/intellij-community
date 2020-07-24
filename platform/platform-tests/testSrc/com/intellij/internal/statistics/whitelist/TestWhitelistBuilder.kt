// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.whitelist

import com.intellij.internal.statistic.eventLog.EventLogBuild
import com.intellij.internal.statistic.service.fus.StatisticsWhitelistConditions
import com.intellij.internal.statistic.service.fus.StatisticsWhitelistGroupConditions
import com.intellij.internal.statistic.service.fus.StatisticsWhitelistGroupConditions.BuildRange
import com.intellij.internal.statistic.service.fus.StatisticsWhitelistGroupConditions.VersionRange

class TestWhitelistBuilder {
  private val groupIds: MutableSet<String> = HashSet()
  private val groupVersions: MutableMap<String, MutableList<VersionRange>> = HashMap()
  private val groupBuilds: MutableMap<String, MutableList<BuildRange>> = HashMap()

  fun addVersion(id: String, from: Int, to: Int): TestWhitelistBuilder {
    if (!groupVersions.containsKey(id)) {
      groupIds.add(id)
      groupVersions[id] = mutableListOf()
    }
    groupVersions[id]!!.add(VersionRange(from, to))
    return this
  }

  fun addVersion(id: String, from: String?, to: String?): TestWhitelistBuilder {
    if (!groupVersions.containsKey(id)) {
      groupIds.add(id)
      groupVersions[id] = mutableListOf()
    }
    groupVersions[id]!!.add(VersionRange.create(from, to))
    return this
  }

  fun addBuild(id: String, from: EventLogBuild?, to: EventLogBuild?): TestWhitelistBuilder {
    if (!groupBuilds.containsKey(id)) {
      groupIds.add(id)
      groupBuilds[id] = mutableListOf()
    }
    groupBuilds[id]!!.add(BuildRange(from, to))
    return this
  }

  fun addBuild(id: String, from: String?, to: String?): TestWhitelistBuilder {
    if (!groupBuilds.containsKey(id)) {
      groupIds.add(id)
      groupBuilds[id] = mutableListOf()
    }
    groupBuilds[id]!!.add(BuildRange.create(from, to))
    return this
  }

  fun addGroup(id: String): TestWhitelistBuilder {
    groupIds.add(id)
    return this
  }

  fun build(): StatisticsWhitelistConditions {
    val result = HashMap<String, StatisticsWhitelistGroupConditions>()
    for (groupId in groupIds) {
      val builds: List<BuildRange> = groupBuilds.getOrDefault(groupId, emptyList())
      val versions: List<VersionRange> = groupVersions.getOrDefault(groupId, emptyList())
      result[groupId] = StatisticsWhitelistGroupConditions(builds, versions)
    }
    return StatisticsWhitelistConditions.create(result)
  }
}