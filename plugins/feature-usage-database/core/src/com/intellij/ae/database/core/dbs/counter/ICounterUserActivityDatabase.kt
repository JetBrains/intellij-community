package com.intellij.ae.database.core.dbs.counter

import com.intellij.ae.database.core.activities.DatabaseBackedCounterUserActivity
import com.intellij.ae.database.core.utils.InstantUtils
import java.time.Instant

interface ICounterUserActivityDatabase {
  suspend fun submit(activity: DatabaseBackedCounterUserActivity, diff: Int, eventTime: Instant = InstantUtils.Now)
}

internal interface IInternalCounterUserActivityDatabase {
  suspend fun submitDirect(activity: DatabaseBackedCounterUserActivity, diff: Int, instant: Instant, extra: Map<String, String>? = null)
  fun executeBeforeConnectionClosed(action: suspend (isFinal: Boolean) -> Unit)
}

interface IReadOnlyCounterUserActivityDatabase {
  suspend fun getActivitySum(activity: DatabaseBackedCounterUserActivity, from: Instant?, until: Instant?): Int
}