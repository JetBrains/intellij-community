// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.sqlite

private const val SQLITE_NULL = 5

class SqliteResultSet(private val statement: SqlitePreparedStatement<*>) {
  /**
   * Checks the status of the result set. True, if it has results and can iterate them; false otherwise.
   */
  var isOpen = false
    internal set

  var row = 0
    private set

  private var pastLastRow = false

  // last column accessed, for wasNull(). -1 if none
  private var lastColumn = 0

  internal fun close(db: NativeDB) {
    row = 0
    pastLastRow = false
    lastColumn = -1
    if (isOpen && !statement.pointer.isClosed) {
      val pointer = statement.pointer
      if (!pointer.isClosed) {
        db.reset(pointer.pointer)
      }
      isOpen = false
    }
  }

  val isClosed: Boolean
    get() = !isOpen

  /**
   * Takes col in [1,x] forms and marks it as last accessed and returns [0,x-1]
   */
  private fun markColumn(column: Int): Int {
    lastColumn = column
    return column
  }

  operator fun next(): Boolean {
    if (!isOpen || pastLastRow) {
      // finished ResultSet
      return false
    }

    lastColumn = -1

    // the first row is loaded by execute(), so do not step() again
    if (row == 0) {
      row++
      return true
    }

    // do the real work
    return when (val statusCode = statement.pointer.safeRunInt(NativeDB::step)) {
      SqliteCodes.SQLITE_DONE -> {
        pastLastRow = true
        false
      }
      SqliteCodes.SQLITE_ROW -> {
        row++
        true
      }
      else -> throw statement.connection.useDb { db -> db.newException(statusCode) }
    }
  }

  ///** @see ResultSet.isAfterLast
  // */
  //val isAfterLast: Boolean
  //  get() = pastLastRow && !emptyResultSet

  ///** @see ResultSet.isBeforeFirst
  // */
  //val isBeforeFirst: Boolean
  //  get() = !emptyResultSet && isOpen && row == 0
  //
  ///** @see ResultSet.isFirst
  // */
  //val isFirst: Boolean
  //  get() = row == 1

  fun wasNull(): Boolean = safeGetColumnType(lastColumn) == SQLITE_NULL

  ///** @see ResultSet.getBigDecimal
  // */
  //fun getBigDecimal(col: Int): BigDecimal? {
  //  return when (safeGetColumnType(checkColumn(col))) {
  //    Codes.SQLITE_NULL -> null
  //    Codes.SQLITE_FLOAT -> BigDecimal.valueOf(safeGetDoubleCol(col))
  //    Codes.SQLITE_INTEGER -> BigDecimal.valueOf(safeGetLongCol(col))
  //    else -> {
  //      val stringValue = safeGetColumnText(col)
  //      try {
  //        BigDecimal(stringValue)
  //      }
  //      catch (e: NumberFormatException) {
  //        throw SQLException("Bad value for type BigDecimal : $stringValue", e)
  //      }
  //    }
  //  }
  //}

  fun getBoolean(zeroBasedColumnIndex: Int): Boolean = getInt(zeroBasedColumnIndex) != 0

  ///** @see ResultSet.getBinaryStream
  // */
  //fun getBinaryStream(col: Int): InputStream? {
  //  return ByteArrayInputStream(getBytes(col) ?: return null)
  //}

  //fun getByte(col: Int): Byte = getInt(col).toByte()

  fun getBytes(col: Int): ByteArray? {
    return statement.pointer.safeRun { db, pointer -> db.column_blob(pointer, markColumn(col)) }
  }

  fun getDouble(col: Int): Double {
    return if (safeGetColumnType(markColumn(col)) == SQLITE_NULL) 0.0 else safeGetDoubleCol(col)
  }

  fun getFloat(col: Int): Float {
    return if (safeGetColumnType(markColumn(col)) == SQLITE_NULL) 0f else safeGetDoubleCol(col).toFloat()
  }

  fun getInt(zeroBasedColumnIndex: Int): Int {
    return statement.pointer.safeRunInt { db, pointer ->
      db.column_int(pointer, markColumn(zeroBasedColumnIndex))
    }
  }

  fun getLong(zeroBasedColumnIndex: Int): Long {
    statement.connection.useDb { db ->
      val pointer = statement.pointer
      pointer.ensureOpen()
      return db.column_long(pointer.pointer, markColumn(zeroBasedColumnIndex))
    }
  }

  fun getString(zeroBasedColumnIndex: Int): String? = safeGetColumnText(zeroBasedColumnIndex)

  private fun safeGetColumnType(zeroBasedColumnIndex: Int): Int {
    return statement.pointer.safeRunInt { db, ptr -> db.column_type(ptr, zeroBasedColumnIndex) }
  }

  private fun safeGetDoubleCol(zeroBasedColumnIndex: Int): Double {
    statement.connection.useDb { db ->
      val pointer = statement.pointer
      pointer.ensureOpen()
      return db.column_double(pointer.pointer, markColumn(zeroBasedColumnIndex))
    }
  }

  private fun safeGetColumnText(zeroBasedColumnIndex: Int): String? {
    return statement.pointer.safeRun { db, pointer -> db.column_text(pointer, markColumn(zeroBasedColumnIndex)) }
  }
}