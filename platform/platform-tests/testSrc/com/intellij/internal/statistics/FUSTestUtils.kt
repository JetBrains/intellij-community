// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.service.fus.FUSWhitelist
import com.intellij.internal.statistic.service.fus.FUSWhitelist.BuildRange
import com.intellij.internal.statistic.service.fus.FUSWhitelist.VersionRange
import com.intellij.openapi.util.BuildNumber

class WhitelistBuilder {
  private val groupIds: MutableSet<String> = HashSet()
  private val groupVersions: MutableMap<String, MutableList<VersionRange>> = HashMap()
  private val groupBuilds: MutableMap<String, MutableList<BuildRange>> = HashMap()

  fun addVersion(id: String, from: Int, to: Int): WhitelistBuilder {
    if (!groupVersions.containsKey(id)) {
      groupIds.add(id)
      groupVersions[id] = mutableListOf()
    }
    groupVersions[id]!!.add(VersionRange(from, to))
    return this
  }

  fun addVersion(id: String, from: String?, to: String?): WhitelistBuilder {
    if (!groupVersions.containsKey(id)) {
      groupIds.add(id)
      groupVersions[id] = mutableListOf()
    }
    groupVersions[id]!!.add(VersionRange.create(from, to))
    return this
  }

  fun addBuild(id: String, from: BuildNumber?, to: BuildNumber?): WhitelistBuilder {
    if (!groupBuilds.containsKey(id)) {
      groupIds.add(id)
      groupBuilds[id] = mutableListOf()
    }
    groupBuilds[id]!!.add(BuildRange(from, to))
    return this
  }

  fun addBuild(id: String, from: String?, to: String?): WhitelistBuilder {
    if (!groupBuilds.containsKey(id)) {
      groupIds.add(id)
      groupBuilds[id] = mutableListOf()
    }
    groupBuilds[id]!!.add(BuildRange.create(from, to))
    return this
  }

  fun build(): FUSWhitelist {
    val result = HashMap<String, FUSWhitelist.GroupFilterCondition>()
    for (groupId in groupIds) {
      groupBuilds.getOrDefault(groupId, emptyList<BuildRange>())
      val builds: List<BuildRange> = groupBuilds.getOrDefault(groupId, emptyList())
      val versions: List<VersionRange> = groupVersions.getOrDefault(groupId, emptyList())
      result[groupId] = FUSWhitelist.GroupFilterCondition(builds, versions)
    }
    return FUSWhitelist.create(result)
  }
}