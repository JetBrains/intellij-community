// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.activities

import com.intellij.ae.database.dbs.counter.CounterUserActivityDatabase


interface CounterUserActivity : UserActivity

abstract class DatabaseBackedCounterUserActivity : CounterUserActivity {
  protected suspend fun getDatabase() = CounterUserActivityDatabase.getInstanceAsync()
}

abstract class WritableDatabaseBackedCounterUserActivity : DatabaseBackedCounterUserActivity() {
  protected suspend fun submit(diff: Int) {
    getDatabase().submit(this, diff)
  }
}