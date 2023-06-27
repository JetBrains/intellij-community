/*
 * Copyright (c) 2007 David Crawshaw <david@zentus.com>
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
@file:Suppress("FunctionName")

package org.jetbrains.sqlite

import com.intellij.openapi.diagnostic.logger
import java.io.IOException
import java.util.*

internal abstract class SqliteDb {
  // tracer for statements to avoid unfinalized statements on db close
  private val statements: MutableSet<SafeStatementPointer> = Collections.newSetFromMap(IdentityHashMap())

  /**
   * Sets a [busy handler](http://www.sqlite.org/c3ref/busy_handler.html) that sleeps
   * for a specified amount of time when a table is locked.
   *
   * @param ms Time to sleep in milliseconds.
   * @see [http://www.sqlite.org/c3ref/busy_timeout.html](http://www.sqlite.org/c3ref/busy_timeout.html)
   */
  abstract fun busy_timeout(ms: Int)

  /**
   * Sets a [busy handler](http://www.sqlite.org/c3ref/busy_handler.html) that sleeps
   * for a specified amount of time when a table is locked.
   *
   * @see [http://www.sqlite.org/c3ref/busy_timeout.html](http://www.sqlite.org/c3ref/busy_handler.html)
   */
  abstract fun busy_handler(busyHandler: BusyHandler?)

  /**
   * Return English-language text that describes the error as either UTF-8 or UTF-16.
   *
   * @return Error description in English.
   * @see [http://www.sqlite.org/c3ref/errcode.html](http://www.sqlite.org/c3ref/errcode.html)
   */
  @Suppress("SpellCheckingInspection")
  abstract fun errmsg(): String?

  /**
   * @return Number of rows that were changed, inserted or deleted by the last SQL statement
   * @see [http://www.sqlite.org/c3ref/changes.html](http://www.sqlite.org/c3ref/changes.html)
   */
  abstract fun changes(): Long

  /**
   * @return Number of row changes caused by INSERT, UPDATE or DELETE statements since the
   * database connection was opened.
   * @see [http://www.sqlite.org/c3ref/total_changes.html](http://www.sqlite.org/c3ref/total_changes.html)
   */
  abstract fun total_changes(): Long

  abstract fun open(file: String, openFlags: Int): Int

  /**
   * Closes a database connection and finalizes any remaining statements before the closing
   * operation.
   *
   * @see [http://www.sqlite.org/c3ref/close.html](http://www.sqlite.org/c3ref/close.html)
   */
  @Synchronized
  fun close() {
    // finalize any remaining statements before closing db
    var error: Throwable? = null
    for (element in statements.toList()) {
      try {
        element.close(this)
      }
      catch (e: Throwable) {
        if (error == null) {
          error = e
        }
        else {
          error.addSuppressed(e)
        }
      }
    }

    if (error != null) {
      logger<SqliteDb>().error(error)
    }

    _close()
  }

  /**
   * Complies an SQL statement.
   * @see [http://www.sqlite.org/c3ref/prepare.html](http://www.sqlite.org/c3ref/prepare.html)
   */
  @Synchronized
  fun addStatement(pointer: SafeStatementPointer): SafeStatementPointer {
    check(statements.add(pointer)) { "Already added pointer to statements set" }
    return pointer
  }

  /**
   * Destroys a statement.
   */
  @Synchronized
  fun finalize(safeStatementPointer: SafeStatementPointer, pointer: Long): Int {
    try {
      return finalize(pointer)
    }
    finally {
      statements.remove(safeStatementPointer)
    }
  }

  /**
   * Creates an SQLite interface to a database with the provided open flags.
   *
   * @param filename  The database to open.
   * @param openFlags File opening configurations ([http://www.sqlite.org/c3ref/c_open_autoproxy.html](http://www.sqlite.org/c3ref/c_open_autoproxy.html))
   * @see [http://www.sqlite.org/c3ref/open.html](http://www.sqlite.org/c3ref/open.html)
   */
  protected abstract fun open(filename: ByteArray, openFlags: Int): Int

  /**
   * Closes the SQLite interface to a database.
   *
   * @see [http://www.sqlite.org/c3ref/close.html](http://www.sqlite.org/c3ref/close.html)
   */
  protected abstract fun _close()

  /**
   * Destroys a prepared statement.
   *
   * @param stmt Pointer to the statement pointer.
   * @return [Result Codes](http://www.sqlite.org/c3ref/c_abort.html)
   * @see [http://www.sqlite.org/c3ref/finalize.html](http://www.sqlite.org/c3ref/finalize.html)
   */
  abstract fun finalize(stmt: Long): Int

  /**
   * Sets a prepared statement object back to its initial state, ready to be re-executed.
   *
   * @param stmt Pointer to the statement.
   * @return [Result Codes](http://www.sqlite.org/c3ref/c_abort.html)
   * @see [http://www.sqlite.org/c3ref/reset.html](http://www.sqlite.org/c3ref/reset.html)
   */
  abstract fun reset(stmt: Long): Int

  /**
   * Reset all bindings on a prepared statement (reset all host parameters to NULL).
   *
   * @param stmt Pointer to the statement.
   * @return [Result Codes](http://www.sqlite.org/c3ref/c_abort.html)
   * @see [http://www.sqlite.org/c3ref/clear_bindings.html](http://www.sqlite.org/c3ref/clear_bindings.html)
   */
  abstract fun clear_bindings(stmt: Long): Int

  /**
   * @param stmt Pointer to the statement.
   * @return Number of parameters in a prepared SQL.
   * @see [http://www.sqlite.org/c3ref/bind_parameter_count.html](http://www.sqlite.org/c3ref/bind_parameter_count.html)
   */
  abstract fun bind_parameter_count(stmt: Long): Int

  /**
   * @param stmt Pointer to the statement.
   * @return Number of columns in the result set returned by the prepared statement.
   * @see [http://www.sqlite.org/c3ref/column_count.html](http://www.sqlite.org/c3ref/column_count.html)
   */
  abstract fun column_count(stmt: Long): Int

  /**
   * @param stmt Pointer to the statement.
   * @param col  Number of columns.
   * @return Datatype code for the initial data type of the result column.
   * @see [http://www.sqlite.org/c3ref/column_blob.html](http://www.sqlite.org/c3ref/column_blob.html)
   */
  abstract fun column_type(stmt: Long, col: Int): Int
  abstract fun column_text(statementPointer: Long, zeroBasedColumnIndex: Int): String?
  abstract fun column_blob(statementPointer: Long, zeroBasedColumnIndex: Int): ByteArray?
  abstract fun column_double(statementPointer: Long, zeroBasedColumnIndex: Int): Double
  abstract fun column_long(statementPointer: Long, zeroBasedColumnIndex: Int): Long
  abstract fun column_int(statementPointer: Long, zeroBasedColumnIndex: Int): Int
  abstract fun bind_null(stmt: Long, oneBasedColumnIndex: Int): Int
  abstract fun bind_int(stmt: Long, oneBasedColumnIndex: Int, v: Int): Int
  abstract fun bind_long(stmt: Long, oneBasedColumnIndex: Int, v: Long): Int
  abstract fun bind_double(stmt: Long, oneBasedColumnIndex: Int, v: Double): Int
  abstract fun bind_text(stmt: Long, oneBasedColumnIndex: Int, v: String): Int
  abstract fun bind_blob(stmt: Long, oneBasedColumnIndex: Int, v: ByteArray?): Int

  /**
   * @param id    The id of the limit.
   * @param value The new value of the limit.
   * @return The prior value of the limit
   * @see [https://www.sqlite.org/c3ref/limit.html](https://www.sqlite.org/c3ref/limit.html)
   */
  abstract fun limit(id: Int, value: Int): Int

  // COMPOUND FUNCTIONS ////////////////////////////////////////////
  abstract fun set_commit_listener(enabled: Boolean)

  abstract fun set_update_listener(enabled: Boolean)

  /**
   * Throws IOException with an error message.
   */
  @Suppress("unused", "SpellCheckingInspection")
  @Throws(IOException::class)
  fun throwex() {
    throw IOException(errmsg())
  }

  @Suppress("SpellCheckingInspection", "unused")
  fun throwex(errorCode: Int) {
    throw newException(errorCode = errorCode, errorMessage = errmsg()!!)
  }

  fun newException(errorCode: Int, sql: ByteArray? = null): SqliteException {
    return newException(errorCode = errorCode, errorMessage = errmsg()!!, sql = sql)
  }

  interface ProgressObserver {
    fun progress(remaining: Int, pageCount: Int)
  }
}

class SqliteException internal constructor(message: String, @Suppress("unused") val resultCode: SqliteErrorCode) : IOException(message)

object SqliteCodes {
  /** Successful result  */
  const val SQLITE_OK = 0

  /** Operation terminated by sqlite3_interrupt() */
  const val SQLITE_INTERRUPT = 9

  /** Library used incorrectly  */
  const val SQLITE_MISUSE = 21

  /** sqlite_step() has another row ready  */
  const val SQLITE_ROW = 100

  /** sqlite_step() has finished executing  */
  const val SQLITE_DONE = 101
}

/**
 * Throws formatted SqliteException with error code and message.
 */
internal fun newException(errorCode: Int, errorMessage: String, sql: ByteArray? = null): SqliteException {
  val code = SqliteErrorCode.getErrorCode(errorCode)
  var text = if (code == SqliteErrorCode.UNKNOWN_ERROR) {
    "$code:$errorCode ($errorMessage)"
  }
  else {
    "$code ($errorMessage)"
  }
  if (sql != null) {
    text += " (sql=${sql.decodeToString()})"
  }
  return SqliteException(message = text, resultCode = code)
}

