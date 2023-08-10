// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.sqlite

import com.intellij.openapi.diagnostic.logger
import org.intellij.lang.annotations.Language

/**
 * Simplifies calling [SqliteStatement.executeBatch] or [SqliteStatement.close] for multiple statements.
 */
class StatementCollection(private val connection: SqliteConnection) {
  private val statements = mutableListOf<SqliteStatement>()

  fun <T : Binder> prepareStatement(@Language("SQLite") sql: String, binder: T): SqlitePreparedStatement<T> {
    val statement = connection.prepareStatement(sql, binder)
    statements.add(statement)
    return statement
  }

  fun prepareIntStatement(@Language("SQLite") sql: String): SqliteIntPreparedStatement {
    val statement = SqliteIntPreparedStatement(connection = connection, sql = sql)
    statements.add(statement)
    return statement
  }

  fun executeBatch() {
    for (statement in statements) {
      statement.executeBatch()
    }
  }

  fun close(performCommit: Boolean) {
    for (statement in statements) {
      try {
        if (performCommit) {
          statement.executeBatch()
        }
        statement.close()
      }
      catch (e: Exception) {
        logger<StatementCollection>().error(e)
      }
    }
  }
}