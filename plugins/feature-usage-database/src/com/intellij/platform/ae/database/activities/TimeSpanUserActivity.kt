// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ae.database.activities

import com.intellij.platform.ae.database.AEDatabaseLifetime
import com.intellij.platform.ae.database.dbs.timespan.TimeSpanUserActivityDatabase
import com.intellij.platform.ae.database.dbs.timespan.TimeSpanUserActivityDatabaseManualKind
import java.time.Instant

interface TimeSpanUserActivity : UserActivity

abstract class DatabaseBackedTimeSpanUserActivity : TimeSpanUserActivity {
  protected val coroutineScope get() = AEDatabaseLifetime.getScope()
  protected suspend fun getDatabase() = TimeSpanUserActivityDatabase.getInstanceAsync()
}

abstract class WritableDatabaseBackedTimeSpanUserActivity : DatabaseBackedTimeSpanUserActivity() {
  abstract val canBeStale: Boolean

  protected suspend fun submitManual(id: String, kind: TimeSpanUserActivityDatabaseManualKind, extra: Map<String, String>?, moment: Instant? = null) {
    getDatabase().submitManual(this, id, kind, canBeStale, extra, moment)
  }

  protected suspend fun submitPeriodic(id: String, extra: Map<String, String>? = null) {
    getDatabase().submitPeriodicEvent(this, id, extra)
  }
}

fun DatabaseBackedTimeSpanUserActivity.toKey(extraKey: String) = "${id}_${extraKey}"