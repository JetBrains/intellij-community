// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.core.activities

import com.intellij.ae.database.core.dbs.counter.CounterUserActivityDatabase
import com.intellij.ae.database.core.utils.InstantUtils
import java.time.Instant


interface CounterUserActivity : UserActivity

abstract class DatabaseBackedCounterUserActivity : CounterUserActivity {
  protected suspend fun getDatabase() = CounterUserActivityDatabase.getInstanceAsync()
}

abstract class WritableDatabaseBackedCounterUserActivity : DatabaseBackedCounterUserActivity() {
  protected suspend fun submit(diff: Int, eventTime: Instant = InstantUtils.Now) {
    getDatabase().submit(this, diff, eventTime)
  }
}