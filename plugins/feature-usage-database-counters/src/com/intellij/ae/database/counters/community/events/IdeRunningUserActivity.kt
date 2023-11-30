// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.counters.community.events

import com.intellij.ae.database.activities.WritableDatabaseBackedTimeSpanUserActivity
import com.intellij.ae.database.dbs.timespan.TimeSpanUserActivityDatabaseManualKind
import com.intellij.ae.database.runUpdateEvent
import com.intellij.ae.database.utils.InstantUtils
import com.intellij.ide.AppLifecycleListener
import kotlinx.coroutines.delay

object IdeRunningUserActivity : WritableDatabaseBackedTimeSpanUserActivity() {
  override val canBeStale = true
  override val id = "ide.running"

  private const val eventId = "ideRun"

  internal suspend fun writeStart() {
    val moment = InstantUtils.Now

    // submit event later, because submitting it now would init all the db stuff
    delay(5000)
    submitManual(eventId, TimeSpanUserActivityDatabaseManualKind.Start, null, moment)
  }
}

internal class IdeStartedUserActivityListener : AppLifecycleListener {
  /**
   * Enough to write down when app was started, because event marked as canBeStale
   */
  override fun appStarted() {
    FeatureUsageDatabaseCountersScopeProvider.getScope().runUpdateEvent(IdeRunningUserActivity) {
      it.writeStart()
    }
  }
}