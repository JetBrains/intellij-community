// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.counters.community.events

import com.intellij.ae.database.activities.ReadableUserActivity
import com.intellij.ae.database.activities.WritableDatabaseBackedCounterUserActivity
import com.intellij.ae.database.runUpdateEvent
import com.intellij.featureStatistics.FeatureStatisticsUpdateListener

/**
 * Stat for 'Code completion has saved you from typing at least N characters'
 */
object CompletionCharactersSpared : ReadableUserActivity<Int>, WritableDatabaseBackedCounterUserActivity() {
  override val id = "completion.spared"

  override suspend fun getActivityValue(): Int {
    return getDatabase().getActivitySum(this, null, null)
  }

  internal suspend fun write(newSpared: Int) {
    submit(newSpared)
  }
}

internal class CompletionCharactersSparedListener : FeatureStatisticsUpdateListener {
  override fun completionStatUpdated(spared: Int) {
    FeatureUsageDatabaseCountersScopeProvider.getScope().runUpdateEvent(CompletionCharactersSpared) {
      it.write(spared)
    }
  }
}