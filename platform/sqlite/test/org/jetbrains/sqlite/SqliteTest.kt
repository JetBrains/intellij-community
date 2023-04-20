// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SqlResolve")

package org.jetbrains.sqlite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
  fun longBinder() {
    connection.execute("""
      create table log (
        commitId integer primary key,
        authorTime integer not null
      ) strict
    """)

    connection.prepareStatement("""
      insert into log(commitId, authorTime) 
      values(?, ?)
    """, LongBinder(2)).use { statement ->
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

    val binder = LongBinder(1, 1)
    connection.prepareStatement("select authorTime from log where commitId = ?", binder).use { statement ->
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
