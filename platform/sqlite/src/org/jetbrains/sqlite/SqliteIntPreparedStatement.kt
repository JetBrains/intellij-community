// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.sqlite

class SqliteIntPreparedStatement internal constructor(private val connection: SqliteConnection,
                                                      private val sql: String) : SqliteStatement {
  private var pointer: SafeStatementPointer?

  private var batchPosition = 0
  private var batch: IntArray

  private val columnCount: Int
  private val paramCount: Int
  private var batchQueryCount: Int

  init {
    lateinit var pointer: SafeStatementPointer
    var columnCount = 0
    var paramCount = 0
    connection.useDb { db ->
      pointer = db.addStatement(SafeStatementPointer(connection = connection, pointer = db.prepare_utf8(sql.encodeToByteArray())))
      columnCount = db.column_count(pointer.pointer)
      paramCount = db.bind_parameter_count(pointer.pointer)
    }

    this.pointer = pointer
    this.columnCount = columnCount
    this.paramCount = paramCount

    require(paramCount > 0)

    batchQueryCount = 0
    // pre-allocate twice more than needed to reduce overall allocations
    batch = IntArray(paramCount * 2)
    batchPosition = 0
  }

  override fun close() {
    connection.useDb { db ->
      val pointer = pointer ?: return
      this.pointer = null
      batchPosition = 0
      val status = pointer.close()
      if (status != SqliteCodes.SQLITE_OK && status != SqliteCodes.SQLITE_MISUSE) {
        throw db.newException(status)
      }
    }
  }

  override fun toString(): String = sql

  fun addBatch() {
    batchPosition += paramCount
    batchQueryCount++
    val batch = batch
    if ((batchPosition + paramCount) > batch.size) {
      val newBatch = IntArray(batch.size * 2)
      batch.copyInto(newBatch)
      this.batch = newBatch
    }
  }

  override fun executeBatch() {
    if (batchQueryCount == 0) {
      return
    }

    try {
      connection.useDb { db ->
        val pointer = pointer ?: throw IllegalStateException("The statement pointer is closed")
        pointer.ensureOpen()
        for (batchIndex in 0 until batchQueryCount) {
          db.reset(pointer.pointer)
          for (position in 0 until paramCount) {
            val status = db.bind_int(pointer.pointer, position + 1, batch[batchIndex * paramCount + position]) and 0xFF
            if (status != SqliteCodes.SQLITE_OK) {
              throw db.newException(status)
            }
          }

          stepInBatch(statementPointer = pointer.pointer, db = db, batchIndex = batchIndex)
        }
        db.reset(pointer.pointer)
      }
    }
    finally {
      clearBatch()
    }
  }

  private fun clearBatch() {
    batchPosition = 0
    batchQueryCount = 0
  }

  fun setInt(position: Int, value: Int) {
    batch[(batchPosition + position) - 1] = value
  }
}
