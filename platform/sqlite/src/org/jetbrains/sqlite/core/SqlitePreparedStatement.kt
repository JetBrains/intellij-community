// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.sqlite.core

import com.intellij.util.ArrayUtilRt
import java.io.IOException
import java.io.InputStream
import kotlin.time.Duration

class SqlitePreparedStatement internal constructor(private val connection: SqliteConnection,
                                                   private val sql: String,
                                                   private val queryTimeout: Duration = Duration.ZERO) : SqliteStatement {
  private val resultSet = SqliteResultSet(this)

  @JvmField
  internal val pointer: SafeStatementPointer

  private var batchPosition = 0
  private var batch: Array<Any?>

  var isClosed = false
    private set

  private val columnCount: Int
  private val paramCount: Int
  private var batchQueryCount: Int

  init {
    val db = connection.db
    synchronized(db) {
      pointer = db.prepareForStatement(sql)
      columnCount = db.column_count(pointer.pointer)
      paramCount = db.bind_parameter_count(pointer.pointer)
    }
    batchQueryCount = 0
    batch = if (paramCount == 0) ArrayUtilRt.EMPTY_OBJECT_ARRAY else arrayOfNulls(paramCount)
    batchPosition = 0
  }

  override fun close() {
    internalClose()
    // isClosed() should only return true when close() happened
    isClosed = true
  }

  internal val db: DB
    get() = connection.db

  private fun internalClose() {
    val pointer = pointer.takeIf { !it.isClosed } ?: return
    check(!connection.isClosed) { "Connection is closed" }

    resultSet.close()
    batch = ArrayUtilRt.EMPTY_OBJECT_ARRAY
    batchPosition = 0
    val status = pointer.close()
    if (status != Codes.SQLITE_OK && status != Codes.SQLITE_MISUSE) {
      throw db.newSQLException(status)
    }
  }

  fun executeQuery(): SqliteResultSet {
    require(columnCount > 0) { "Query does not return results" }
    pointer.ensureOpen()
    resultSet.close()
    connection.withConnectionTimeout(queryTimeout) {
      // SqliteResultSet is responsible for `db.reset`, that's why here we do not have try-finally as `executeLifecycle` does
      synchronized(this.db) {
        bindParams()
        val isEmpty = connection.step(pointer.pointer, sql)
        if (!isEmpty) {
          resultSet.isOpen = true
        }
        return resultSet
      }
    }
  }

  fun selectBoolean(): Boolean {
    executeLifecycle { isEmpty ->
      return !isEmpty && db.column_int(pointer.pointer, 1) != 0
    }
  }

  private inline fun <T> executeLifecycle(executor: (isEmpty: Boolean) -> T): T {
    pointer.ensureOpen()
    connection.withConnectionTimeout(queryTimeout) {
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
  }

  private fun bindParams() {
    for ((position, value) in batch.withIndex()) {
      sqlBind(pointer.pointer, position, value, db)
    }
  }

  override fun toString(): String = "$sql \n parameters=${batch.contentToString()}"

  fun addBatch() {
    pointer.ensureOpen()
    batchPosition += paramCount
    batchQueryCount++
    val batch = batch
    if ((batchPosition + paramCount) > batch.size) {
      val newBatch = arrayOfNulls<Any?>(batch.size * 2)
      batch.copyInto(newBatch)
      this.batch = newBatch
    }
  }

  override fun executeBatch() {
    if (batchQueryCount == 0) {
      return
    }

    connection.withConnectionTimeout(queryTimeout) {
      try {
        synchronized(pointer.db) {
          pointer.ensureOpen()
          for (batchIndex in 0 until batchQueryCount) {
            db.reset(pointer.pointer)
            for (position in 0 until paramCount) {
              sqlBind(pointer.pointer, position, batch[batchIndex * paramCount + position], db)
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
  }

  //fun clearParameters() {
  //  checkOpen()
  //  pointer!!.safeRun(DB::clear_bindings)
  //  batch.fill(null, batchPosition, batchPosition + paramCount)
  //}

  @Suppress("MemberVisibilityCanBePrivate")
  fun clearBatch() {
    batchPosition = 0
    batch.fill(null)
    batchQueryCount = 0
  }

  /**
   * Assigns the object value to the element at the specific position of the array batch.
   */
  private fun batch(position: Int, value: Any?) {
    batch[(batchPosition + position) - 1] = value
  }

  //fun setBinaryStream(pos: Int, input: InputStream?, length: Int) {
  //  if (input == null && length == 0) {
  //    setBytes(pos, null)
  //  }
  //  setBytes(pos, readBytes(input, length))
  //}

  //fun setUnicodeStream(pos: Int, input: InputStream?, length: Int) {
  //  if (input == null && length == 0) {
  //    setString(pos, null)
  //  }
  //  setString(pos, readBytes(input, length).decodeToString())
  //}

  //fun setBoolean(pos: Int, value: Boolean) {
  //  setInt(pos, if (value) 1 else 0)
  //}

  //fun setByte(pos: Int, value: Byte) {
  //  setInt(pos, value.toInt())
  //}

  fun setBytes(pos: Int, value: ByteArray?) {
    batch(pos, value)
  }

  //fun setDouble(pos: Int, value: Double) {
  //  batch(pos, value)
  //}

  //fun setFloat(position: Int, value: Float) {
  //  batch(position, value)
  //}

  fun setInt(position: Int, value: Int) {
    batch(position = position, value = value)
  }

  fun setLong(pos: Int, value: Long) {
    batch(pos, value)
  }

  fun setNull(pos: Int) {
    batch(pos, null)
  }

  //fun setShort(pos: Int, value: Short) {
  //  setInt(pos, value.toInt())
  //}

  fun setString(pos: Int, value: String?) {
    batch(pos, value)
  }
}

/**
 * Reads given number of bytes from an input stream.
 *
 * @param input The input stream.
 * @param length  The number of bytes to read.
 * @return byte array.
 */
private fun readBytes(input: InputStream, length: Int): ByteArray {
  require(length >= 0) { "Error reading stream. Length should be non-negative" }

  val bytes = ByteArray(length)
  var bytesRead: Int
  var totalBytesRead = 0
  while (totalBytesRead < length) {
    bytesRead = input.read(bytes, totalBytesRead, length - totalBytesRead)
    if (bytesRead == -1) {
      throw IOException("End of stream has been reached")
    }
    totalBytesRead += bytesRead
  }
  return bytes
}