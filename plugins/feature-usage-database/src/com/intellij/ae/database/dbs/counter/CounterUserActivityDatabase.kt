// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SqlResolve")

package com.intellij.ae.database.dbs.counter

import com.intellij.ae.database.IdService
import com.intellij.ae.database.activities.DatabaseBackedCounterUserActivity
import com.intellij.ae.database.dbs.*
import com.intellij.ae.database.utils.InstantUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.sqlite.ObjectBinderFactory
import org.jetbrains.sqlite.SqliteConnection
import java.time.Instant

/**
 * Database for storing and retrieving counter-based events.
 *
 * All events are rounded to a minute, seconds and milliseconds are always 0
 */
@Service
class CounterUserActivityDatabase(cs: CoroutineScope) : ICounterUserActivityDatabase,
                                                        IUserActivityDatabaseLayer,
                                                        IReadOnlyCounterUserActivityDatabase,
                                                        IInternalCounterUserActivityDatabase,
                                                        ISqliteExecutor, ISqliteBackedDatabaseLayer {
  companion object {
    internal suspend fun getInstanceAsync() = serviceAsync<CounterUserActivityDatabase>()
  }

  private val throttler = CounterUserActivityDatabaseThrottler(cs, this, runBackgroundUpdater = !ApplicationManager.getApplication().isUnitTestMode)

  /**
   * Retrieves the activity for a user based on the provided activity ID and time range.
   * Answers the question 'How many times did activity happen in given timeframe?'
   */
  override suspend fun getActivitySum(activity: DatabaseBackedCounterUserActivity, from: Instant?, until: Instant?): Int {
    val nnFrom = InstantUtils.formatForDatabase(from ?: InstantUtils.SomeTimeAgo)
    val nnUntil = InstantUtils.formatForDatabase(until ?: InstantUtils.NowButABitLater)

    return execute { connection ->
      val getActivityStatement = connection.prepareStatement(
        "SELECT sum(diff) FROM counterUserActivity WHERE activity_id = ? AND created_at >= ? AND created_at <= ?",
        ObjectBinderFactory.create3<String, String, String>()
      )
      throttler.commitChanges()

      getActivityStatement.binder.bind(activity.id, nnFrom, nnUntil)
      getActivityStatement.selectInt() ?: 0
    } ?: 0
  }

  /**
   * Main entry point for submitting new event update
   *
   * This method doesn't submit to database, but first submits to throttler which
   */
  override suspend fun submit(activity: DatabaseBackedCounterUserActivity, diff: Int, eventTime: Instant) {
    thisLogger().info("${activity.id} = $diff")
    throttler.submit(activity, diff, eventTime)
  }

  /**
   * Writes event directly to database. Very internal API!
   */
  override suspend fun submitDirect(activity: DatabaseBackedCounterUserActivity, diff: Int, instant: Instant, extra: Map<String, String>?) {
    execute { database, metadata ->
      val updateActivityStatement = database.prepareStatement(
        "INSERT INTO counterUserActivity (activity_id, diff, created_at, ide_id) VALUES (?, ?, ?, ?)",
        ObjectBinderFactory.create4<String, Int, String, Int>()
      )
      updateActivityStatement.binder.bind(activity.id, diff, InstantUtils.formatForDatabase(instant), IdService.getInstance().getDatabaseId(metadata))
      updateActivityStatement.executeUpdate()
    }
  }

  override fun executeBeforeConnectionClosed(action: suspend () -> Unit) {
    SqliteLazyInitializedDatabase.getInstance().executeBeforeConnectionClosed(action)
  }

  override suspend fun <T> execute(action: suspend (initDb: SqliteConnection) -> T): T? {
    return SqliteLazyInitializedDatabase.getInstanceAsync().execute(action)
  }

  private suspend fun <T> execute(action: suspend (initDb: SqliteConnection, metadata: SqliteDatabaseMetadata) -> T): T? {
    return SqliteLazyInitializedDatabase.getInstanceAsync().execute(action)
  }

  override val tableName: String = "counterUserActivity"
}
