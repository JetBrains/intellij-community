// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.sqlite

class SqliteIntPreparedStatement internal constructor(private val connection: SqliteConnection, private val sql: String) : SqliteStatement {
  private val pointer: SafeStatementPointer
  val binder: IntBinder

  init {
    lateinit var pointer: SafeStatementPointer
    var paramCount = 0
    connection.useDb { db ->
      pointer = db.addStatement(SafeStatementPointer(connection = connection, pointer = db.prepare_utf8(sql.encodeToByteArray())))
      paramCount = db.bind_parameter_count(pointer.pointer)
    }
    require(paramCount > 0)

    this.pointer = pointer
    // pre-allocate twice more than needed to reduce overall allocations
    binder = IntBinder(paramCount, paramCount * 2)
  }

  override fun close() {
    connection.useDb { db ->
      pointer.close(db)
    }
  }

  override fun toString(): String = sql

  fun ensureCapacity(count: Int) {
    binder.ensureCapacity(count)
  }

  fun addBatch() {
    binder.addBatch()
  }

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
