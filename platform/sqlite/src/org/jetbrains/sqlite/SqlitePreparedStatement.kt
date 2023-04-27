// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.sqlite

class SqlitePreparedStatement<T : Binder> internal constructor(private val connection: SqliteConnection,
                                                               private val sql: ByteArray,
                                                               val binder: T) : SqliteStatement {
  private val resultSet = SqliteResultSet(this)

  @JvmField
  internal val pointer: SafeStatementPointer

  var isClosed = false
    private set

  private val columnCount: Int

  init {
    val db = connection.db
    val paramCount = synchronized(db) {
      pointer = db.prepareForStatement(sql)
      columnCount = db.column_count(pointer.pointer)
      db.bind_parameter_count(pointer.pointer)
    }
    if (paramCount != binder.paramCount) {
      pointer.close()
      throw IllegalStateException("statement param count: $paramCount, binder param count: ${binder.paramCount}")
    }
  }

  override fun close() {
    internalClose()
    // isClosed() should only return true when close() happened
    isClosed = true
  }

  internal val db: SqliteDb
    get() = connection.db

  private fun internalClose() {
    val pointer = pointer.takeIf { !it.isClosed } ?: return
    check(!connection.isClosed) { "Connection is closed" }

    resultSet.close()
    binder.clearBatch()
    val status = pointer.close()
    if (status != SqliteCodes.SQLITE_OK && status != SqliteCodes.SQLITE_MISUSE) {
      throw db.newException(status)
    }
  }

  fun executeQuery(): SqliteResultSet {
    require(columnCount > 0) { "Query does not return results" }
    pointer.ensureOpen()
    resultSet.close()
    // SqliteResultSet is responsible for `db.reset`, that's why here we do not have try-finally as `executeLifecycle` does
    synchronized(db) {
      bindParams()
      val isEmpty = connection.step(pointer.pointer, sql)
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
    pointer.ensureOpen()
    synchronized(db) {
      bindParams()
      try {
        connection.step(pointer.pointer, sql)
      }
      finally {
        db.reset(pointer.pointer)
      }
    }
  }

  fun selectBoolean(): Boolean {
    executeLifecycle { isEmpty ->
      return !isEmpty && db.column_int(pointer.pointer, 0) != 0
    }
  }

  fun selectByteArray(): ByteArray? {
    executeLifecycle { isEmpty ->
      return if (isEmpty) null else db.column_blob(pointer.pointer, 0)
    }
  }

  private inline fun <T> executeLifecycle(executor: (isEmpty: Boolean) -> T): T {
    pointer.ensureOpen()
    synchronized(db) {
      try {
        bindParams()
        val isEmpty = connection.step(pointer.pointer, sql)
        return executor(isEmpty)
      }
      finally {
        db.reset(pointer.pointer)
      }
    }
  }

  private fun bindParams() {
    binder.bindParams(pointer.pointer, db)
  }

  override fun toString(): String = "$sql \n parameters=$binder"

  override fun executeBatch() {
    if (binder.batchQueryCount == 0) {
      return
    }

    try {
      synchronized(db) {
        pointer.ensureOpen()
        binder.executeBatch(pointer.pointer, db)
        db.reset(pointer.pointer)
      }
    }
    finally {
      binder.clearBatch()
    }
  }
}