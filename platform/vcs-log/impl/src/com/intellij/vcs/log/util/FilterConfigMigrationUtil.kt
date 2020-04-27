// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.util

import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties.RecentGroup
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject


object FilterConfigMigrationUtil {
  private const val RECENT_USER_FILTER_NAME = "User"

  @JvmStatic
  fun migrateRecentUserFilters(recentFilters: MutableMap<String, MutableList<RecentGroup>>) {
    val recentUserFilterGroups: MutableList<RecentGroup> = recentFilters[RECENT_USER_FILTER_NAME] ?: return

    recentFilters[RECENT_USER_FILTER_NAME] = recentUserFilterGroups
      .map { RecentGroup(migrateOldMeFilters(it.FILTER_VALUES)) }
      .toMutableList()
  }

  @JvmStatic
  fun migrateTabUserFilters(filters: MutableMap<String, MutableList<String>>) {
    val userFilters: MutableList<String> = filters[VcsLogFilterCollection.USER_FILTER.name] ?: return
    filters[VcsLogFilterCollection.USER_FILTER.name] = migrateOldMeFilters(userFilters).toMutableList()
  }

  private fun migrateOldMeFilters(userFilters: List<String>): List<String> {
    return userFilters.map { if (it == "me") VcsLogFilterObject.ME else it }
  }
}

