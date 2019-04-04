// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.service.fus.FUSWhitelist
import com.intellij.util.containers.ContainerUtil

class WhitelistBuilder {
  val groups: MutableMap<String, List<FUSWhitelist.VersionRange>> = ContainerUtil.newHashMap()

  fun add(id: String): WhitelistBuilder {
    groups[id] = ContainerUtil.emptyList()
    return this
  }

  fun add(id: String, vararg versions: FUSWhitelist.VersionRange): WhitelistBuilder {
    val versionsList = ContainerUtil.newArrayList<FUSWhitelist.VersionRange>()
    for (version in versions) {
      versionsList.add(version)
    }
    groups[id] = versionsList
    return this
  }

  fun build(): FUSWhitelist {
    return FUSWhitelist.create(groups)
  }
}