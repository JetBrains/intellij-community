// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SqlResolve")

package org.jetbrains.sqlite

import kotlinx.coroutines.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Condition
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import kotlin.time.Duration.Companion.milliseconds

class SqliteTest {
  private lateinit var connection: SqliteConnection

  @BeforeEach
  fun openDb() {
    connection = SqliteConnection(file = null)
  }

  @AfterEach
  fun closeDb() {
    connection.close()
  }

  @Test
  fun insert() {
    testInsert(connection)
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.MINUTES)
  fun interrupt(): Unit = runBlocking(Dispatchers.Default) {
    val started = CompletableDeferred<Boolean>()
    val sqlJob = async(Dispatchers.IO) {
      started.complete(true)
      assertThatThrownBy {
        connection.execute("""
      WITH RECURSIVE r(i) AS (
        VALUES(0)
        UNION ALL
        SELECT i FROM r
        LIMIT 1000000000
      )
      SELECT i FROM r WHERE i = 1
    """.trimIndent())
      }.`is`(Condition(Predicate { it is SqliteException && it.resultCode == SqliteErrorCode.SQLITE_INTERRUPT }, ""))
    }

    started.await()
    delay(100.milliseconds)
    connection.interruptAndClose()
    if (sqlJob.isActive) {
      delay(100.milliseconds)
    }
    assertThat(sqlJob.isCancelled)
  }

  @Test
  fun boolean() {
    connection.execute("create table test (c1 integer) strict")

    connection.prepareStatement("insert into test(c1) values(?)", ObjectBinder(1)).use { statement ->
      statement.binder.bind(22)
      statement.executeUpdate()
    }

    connection.prepareStatement("select c1 from test", EmptyBinder).use { statement ->
      assertThat(statement.selectBoolean()).isTrue()
    }
  }

  @Test
  fun byteArray() {
    connection.execute("create table test (c1 blob) strict")

    connection.prepareStatement("insert into test(c1) values(?)", ObjectBinder(1)).use { statement ->
      statement.binder.bind(ByteArray(42))
      statement.executeUpdate()
    }

    connection.prepareStatement("select c1 from test", EmptyBinder).use { statement ->
      assertThat(statement.selectByteArray()).hasSize(42)
    }
  }

  @Test
  fun intPreparedStatement() {
    connection.execute("""
      create table log (
        a integer not null,
        b integer not null,
        c integer not null
      ) strict
    """)

    val rowCount = 100
    val columnCount = 3

    val statementCollection = StatementCollection(connection)
    try {
      val statement = statementCollection.prepareIntStatement("insert into log(a, b, c) values(?, ?, ?)")
      val random = Random(42)
      repeat(rowCount) {
        statement.binder.bind(random.nextInt(), random.nextInt(), random.nextInt())
        statement.addBatch()
      }
      statement.executeBatch()
    }
    finally {
      statementCollection.close(true)
    }

    connection.prepareStatement("select a, b, c from log order by rowid", EmptyBinder).use { statement ->
      val random = Random(42)
      val resultSet = statement.executeQuery()
      var count = 0
      while (resultSet.next()) {
        count++
        repeat(columnCount) {
          assertThat(resultSet.getInt(it)).isEqualTo(random.nextInt())
        }
      }
      assertThat(count == rowCount)
    }
  }

  @Test
  fun intBinder() {
    connection.execute("""
      create table log (
        a integer not null,
        b integer not null,
        c integer not null
      ) strict
    """)

    val rowCount = 100
    val columnCount = 3

    val binder = IntBinder(3)
    connection.prepareStatement("insert into log(a, b, c) values(?, ?, ?)", binder).use { statement ->
      val random = Random(42)
      repeat(rowCount) {
        binder.bind(random.nextInt(), random.nextInt(), random.nextInt())
        binder.addBatch()
      }
      statement.executeBatch()
    }

    connection.prepareStatement("select a, b, c from log order by rowid", EmptyBinder).use { statement ->
      val random = Random(42)
      val resultSet = statement.executeQuery()
      var count = 0
      while (resultSet.next()) {
        count++
        repeat(columnCount) {
          assertThat(resultSet.getInt(it)).isEqualTo(random.nextInt())
        }
      }
      assertThat(count == rowCount)
    }
  }

  @Test
  fun longBinder() {
    connection.execute("""
      create table log (
        commitId integer primary key,
        authorTime integer not null
      ) strict
    """)

    connection.prepareStatement("insert into log(commitId, authorTime) values(?, ?)", LongBinder(2)).use { statement ->
      statement.binder.bind(12, 42)
      statement.binder.addBatch()

      statement.binder.bind(13, 42)
      statement.binder.addBatch()
      statement.executeBatch()
    }

    connection.prepareStatement("select count(authorTime) from log", EmptyBinder).use { statement ->
      val resultSet = statement.executeQuery()
      assertThat(resultSet.next()).isTrue()
      assertThat(resultSet.getInt(0)).isEqualTo(2)
    }

    connection.statementPool("select authorTime from log where commitId = ?") { LongBinder(1, 1) }.use { statement, binder ->
      // test empty
      for (key in longArrayOf(12, 13)) {
        binder.bind(key + 12)
        val resultSet = statement.executeQuery()

        if (resultSet.next()) {
          resultSet.getLong(0)
        }
      }
      for (key in longArrayOf(12, 13)) {
        binder.bind(key)
        val resultSet = statement.executeQuery()
        assertThat(resultSet.next()).isTrue()
        assertThat(resultSet.getLong(0)).isEqualTo(42)
      }
    }
  }
}

internal fun testInsert(connection: SqliteConnection) {
  connection.execute("""
      create table log (
        commitId integer primary key,
        message text not null,
        authorTime integer not null,
        commitTime integer not null,
        committerId integer null
      ) strict
    """)

  connection.prepareStatement("""
      insert into log(commitId, message, authorTime, commitTime, committerId) 
      values(?, ?, ?, ?, ?) 
      on conflict(commitId) do update set message=excluded.message
    """, ObjectBinder(5)).use { statement ->
    statement.binder.bind(12, "test", 2, 2, 1)
    statement.binder.addBatch()
    statement.executeBatch()
  }

  connection.prepareStatement("select message from log where commitId = ?", IntBinder(paramCount = 1)).use { statement ->
    statement.binder.bind(12)
    val resultSet = statement.executeQuery()
    assertThat(resultSet.next()).isTrue()
    assertThat(resultSet.getString(0)).isEqualTo("test")
  }
}
