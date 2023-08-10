// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.sqlite

class SqlitePreparedStatement<T : Binder> internal constructor(@JvmField internal val connection: SqliteConnection,
                                                               private val sql: ByteArray,
                                                               val binder: T) : SqliteStatement {
  private val resultSet = SqliteResultSet(this)

  @JvmField
  internal val pointer: SafeStatementPointer

  private val columnCount: Int

  init {
    lateinit var pointer: SafeStatementPointer
    var columnCount = 0
    connection.useDb { db ->
      pointer = db.addStatement(SafeStatementPointer(connection = connection, pointer = db.prepare_utf8(sql)))
      columnCount = db.column_count(pointer.pointer)
      val paramCount = db.bind_parameter_count(pointer.pointer)
      if (paramCount != binder.paramCount) {
        pointer.close(db)
        throw IllegalStateException("statement param count: $paramCount, binder param count: ${binder.paramCount}")
      }
    }

    this.pointer = pointer
    this.columnCount = columnCount
  }

  override fun close() {
    connection.useDb(::close)
  }

  internal fun close(db: NativeDB) {
    val pointer = pointer.takeIf { !it.isClosed } ?: return
    resultSet.close(db)
    binder.clearBatch()
    pointer.close(db)
  }

  fun executeQuery(): SqliteResultSet {
    require(columnCount > 0) { "Query does not return results" }
    // SqliteResultSet is responsible for `db.reset`, that's why here we do not have try-finally as `executeLifecycle` does
    connection.useDb { db ->
      pointer.ensureOpen()
      resultSet.close(db)

      bindParams(db)
      val isEmpty = step(statementPointer = pointer.pointer, sql = sql, db = db)
      if (isEmpty) {
        // SQLITE_DONE means that the statement has finished executing successfully.
        // sqlite3_step() should not be called again on this virtual machine without first calling sqlite3_reset()
        // to reset the virtual machine back to its initial state.

        // resultSet.close() do this, but as we do not set `isOpen` to `true`, we have to reset it right now
        db.reset(pointer.pointer)
      }
      else {
        resultSet.isOpen = true
      }
      return resultSet
    }
  }

  fun executeUpdate() {
    connection.useDb { db ->
      pointer.ensureOpen()
      bindParams(db)
      try {
        step(statementPointer = pointer.pointer, sql = sql, db = db)
      }
      finally {
        db.reset(pointer.pointer)
      }
    }
  }

  fun selectBoolean(): Boolean {
    executeLifecycle { db, isEmpty ->
      return !isEmpty && db.column_int(pointer.pointer, 0) != 0
    }
  }

  fun selectInt(): Int? {
    executeLifecycle { db, isEmpty ->
      return if (isEmpty) null else db.column_int(pointer.pointer, 0)
    }
  }

  fun selectNotNullInt(): Int {
    executeLifecycle { db, isEmpty ->
      return if (isEmpty) throw IllegalStateException("Must be not empty") else db.column_int(pointer.pointer, 0)
    }
  }

  fun selectByteArray(): ByteArray? {
    executeLifecycle { db, isEmpty ->
      return if (isEmpty) null else db.column_blob(pointer.pointer, 0)
    }
  }

  private inline fun <T> executeLifecycle(executor: (db: NativeDB, isEmpty: Boolean) -> T): T {
    connection.useDb { db ->
      pointer.ensureOpen()
      try {
        bindParams(db)
        val isEmpty = step(statementPointer = pointer.pointer, sql = sql, db = db)
        return executor(db, isEmpty)
      }
      finally {
        db.reset(pointer.pointer)
      }
    }
  }

  private fun bindParams(db: NativeDB) {
    binder.bindParams(pointer.pointer, db)
  }

  override fun toString(): String = "$sql \n parameters=$binder"

  override fun executeBatch() {
    if (binder.batchQueryCount == 0) {
      return
    }

    try {
      connection.useDb { db ->
        pointer.ensureOpen()
        binder.executeBatch(pointer.pointer, db)
      }
    }
    finally {
      binder.clearBatch()
    }
  }
}