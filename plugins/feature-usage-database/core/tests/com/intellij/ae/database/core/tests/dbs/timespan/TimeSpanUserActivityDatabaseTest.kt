// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.core.tests.dbs.timespan

import com.intellij.ae.database.core.activities.DatabaseBackedTimeSpanUserActivity
import com.intellij.ae.database.core.dbs.SqliteLazyInitializedDatabase
import com.intellij.ae.database.core.dbs.timespan.TimeSpanUserActivityDatabase
import com.intellij.ae.database.core.tests.dbs.runDatabaseLayerTest
import com.intellij.ae.database.core.utils.InstantUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.sqlite.ObjectBinderFactory
import org.junit.jupiter.api.Assertions
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

// TODO fix broken test
class TimeSpanUserActivityDatabaseTest : BasePlatformTestCase() {
  private val databaseFactory = { cs: CoroutineScope ->
    TimeSpanUserActivityDatabase(cs)
  }

  private data class TestEvent(
    val dbId: Int,
    val activityId: String,
    val startedAt: String,
    val endAt: String,
    val isFinished: Boolean,
  )

  private suspend fun SqliteLazyInitializedDatabase.getActivityEvents(activityId: String): List<TestEvent> {
    return execute { db ->
      val stmt = db.prepareStatement(
        """ 
      SELECT id, activity_id, started_at, ended_at, is_finished
      FROM "timespanUserActivity"
      WHERE activity_id = ?
      """.trimIndent(),
        ObjectBinderFactory.create1<String>(),
      )

      val result = mutableListOf<TestEvent>()
      stmt.binder.bind(activityId)
      stmt.executeQuery().let {
        while (it.next()) {
          result.add(TestEvent(
            it.getInt(0),
            it.getString(1)!!,
            it.getString(2)!!,
            it.getString(3)!!,
            it.getBoolean(4),
          ))
        }
      }

      result
    } ?: error("Unexpected null in getActivityEvents")
  }

  fun testEndEventInternal() = runDatabaseLayerTest(databaseFactory) { db, initDb, cs ->
    val testActivity = object : DatabaseBackedTimeSpanUserActivity() {
      override val id: String get() = "testActivity1"
    }

    val initialStart = InstantUtils.Now
    val initialEnd = InstantUtils.NowButABitLater
    val insertedId = db.endEventInternal(null, testActivity, initialStart, initialEnd, isFinished = false)


    // Assert event stored correctly on first call
    val storedEvents1 = initDb.getActivityEvents(testActivity.id)
    Assertions.assertEquals(1, storedEvents1.size)
    val storedEvent1 = storedEvents1.first()
    Assertions.assertEquals(insertedId, storedEvent1.dbId)
    Assertions.assertEquals(testActivity.id, storedEvent1.activityId)
    Assertions.assertFalse(storedEvent1.isFinished)
    Assertions.assertEquals(InstantUtils.formatForDatabase(initialStart), storedEvent1.startedAt)
    Assertions.assertEquals(InstantUtils.formatForDatabase(initialEnd), storedEvent1.endAt)

    val updatedEnd = initialEnd + 10.seconds.toJavaDuration()
    val updatedId = db.endEventInternal(insertedId, testActivity, initialEnd, updatedEnd, isFinished = true)

    // Assert id didn't changeb
    Assertions.assertEquals(insertedId, updatedId)

    // Assert event updated correctly on consequent calls
    val storedEvents2 = initDb.getActivityEvents(testActivity.id)
    Assertions.assertEquals(1, storedEvents2.size)
    val storedEvent2 = storedEvents2.first()
    Assertions.assertEquals(updatedId, storedEvent2.dbId)
    Assertions.assertEquals(testActivity.id, storedEvent2.activityId)
    Assertions.assertTrue(storedEvent2.isFinished)
    Assertions.assertEquals(InstantUtils.formatForDatabase(initialStart), storedEvent2.startedAt)
    Assertions.assertEquals(InstantUtils.formatForDatabase(updatedEnd), storedEvent2.endAt)
  }
}