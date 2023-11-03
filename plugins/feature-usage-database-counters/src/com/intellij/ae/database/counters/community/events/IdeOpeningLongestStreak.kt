package com.intellij.ae.database.counters.community.events

import com.intellij.platform.ae.database.activities.DatabaseBackedTimeSpanUserActivity
import com.intellij.platform.ae.database.activities.ReadableUserActivity
import com.intellij.platform.ae.database.models.TimeSpan

object IdeOpeningLongestStreak : ReadableUserActivity<TimeSpan>, DatabaseBackedTimeSpanUserActivity() {
  override val id = "ide.opening.longest.streak"

  override suspend fun get(): TimeSpan {
    return getDatabase().getLongestActivity(IdeRunningUserActivity, null, null)
  }
}