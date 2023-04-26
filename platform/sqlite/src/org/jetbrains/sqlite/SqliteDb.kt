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

import com.intellij.openapi.diagnostic.Logger
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

internal abstract class SqliteDb {
  // tracer for statements to avoid unfinalized statements on db close
  private val statements = ConcurrentHashMap.newKeySet<SafeStatementPointer>()
  private val updateListeners = HashSet<SQLiteUpdateListener>()
  private val commitListeners = HashSet<SQLiteCommitListener>()

  companion object {
    /**
     * Throws formatted SqliteException with error code and message.
     */
    fun newException(errorCode: Int, errorMessage: String, sql: ByteArray? = null): SqliteException {
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
  }

  /**
   * Aborts any pending operation and returns at its earliest opportunity.
   * See [http://www.sqlite.org/c3ref/interrupt.html](http://www.sqlite.org/c3ref/interrupt.html)
   */
  abstract fun interrupt()

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
   * Returns the value for SQLITE_VERSION, SQLITE_VERSION_NUMBER, and SQLITE_SOURCE_ID C
   * preprocessor macros that are associated with the library.
   *
   * @see [http://www.sqlite.org/c3ref/c_source_id.html](http://www.sqlite.org/c3ref/c_source_id.html)
   */
  @Suppress("SpellCheckingInspection")
  abstract fun libversion(): String

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

  /**
   * Enables or disables loading of SQLite extensions.
   *
   * @param enable True to enable; false otherwise.
   * @return [Result Codes](http://www.sqlite.org/c3ref/c_abort.html)
   * @see [http://www.sqlite.org/c3ref/load_extension.html](http://www.sqlite.org/c3ref/load_extension.html)
   */
  abstract fun enable_load_extension(enable: Boolean): Int

  /**
   * Execute an SQL statement using the process of compiling, evaluating, and destroying the prepared statement object.
   *
   * @param sql SQL statement to be executed.
   * @see [http://www.sqlite.org/c3ref/exec.html](http://www.sqlite.org/c3ref/exec.html)
   */
  @Synchronized
  fun exec(sql: ByteArray) {
    val status = _exec(sql)
    if (status != SqliteCodes.SQLITE_OK) {
      throw newException(errorCode = status, errorMessage = errmsg()!!, sql = sql)
    }
  }

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
    for (element in statements) {
      try {
        element.internalClose()
      }
      catch (e: Throwable) {
        Logger.getInstance(SqliteDb::class.java).error(e)
      }
    }
    _close()
  }

  /**
   * Complies an SQL statement.
   * @see [http://www.sqlite.org/c3ref/prepare.html](http://www.sqlite.org/c3ref/prepare.html)
   */
  @Synchronized
  fun prepareForStatement(sql: ByteArray): SafeStatementPointer {
    val pointer = prepare(sql)
    check(statements.add(pointer)) { "Already added pointer to statements set" }
    return pointer
  }

  /**
   * Destroys a statement.
   *
   * @param safePtr the pointer wrapper to remove from internal structures
   * @param ptr     the raw pointer to free
   * @return [Result Codes](http://www.sqlite.org/c3ref/c_abort.html)
   * @see [http://www.sqlite.org/c3ref/finalize.html](http://www.sqlite.org/c3ref/finalize.html)
   */
  @Synchronized
  fun finalize(safePtr: SafeStatementPointer, ptr: Long): Int {
    try {
      return finalize(ptr)
    }
    finally {
      statements.remove(safePtr)
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
   * Complies, evaluates, executes and commits an SQL statement.
   *
   * @param sql An SQL statement.
   * @return [Result Codes](http://www.sqlite.org/c3ref/c_abort.html)
   * @see [http://www.sqlite.org/c3ref/exec.html](http://www.sqlite.org/c3ref/exec.html)
   */
  abstract fun _exec(sql: ByteArray): Int

  protected abstract fun prepare(sql: ByteArray): SafeStatementPointer

  /**
   * Destroys a prepared statement.
   *
   * @param stmt Pointer to the statement pointer.
   * @return [Result Codes](http://www.sqlite.org/c3ref/c_abort.html)
   * @see [http://www.sqlite.org/c3ref/finalize.html](http://www.sqlite.org/c3ref/finalize.html)
   */
  abstract fun finalize(stmt: Long): Int

  /**
   * Evaluates a statement.
   *
   * @param stmt Pointer to the statement.
   * @return [Result Codes](http://www.sqlite.org/c3ref/c_abort.html)
   * @see [http://www.sqlite.org/c3ref/step.html](http://www.sqlite.org/c3ref/step.html)
   */
  abstract fun step(stmt: Long): Int

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
   * @param col  Number of column.
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
  abstract fun bind_text(stmt: Long, oneBasedColumnIndex: Int, v: String?): Int
  abstract fun bind_blob(stmt: Long, oneBasedColumnIndex: Int, v: ByteArray?): Int

  /**
   * Sets the result of an SQL function as NULL with the pointer to the SQLite database context.
   *
   * @param context Pointer to the SQLite database context.
   * @see [http://www.sqlite.org/c3ref/result_blob.html](http://www.sqlite.org/c3ref/result_blob.html)
   */
  abstract fun result_null(context: Long)

  /**
   * Sets the result of an SQL function as text data type with the pointer to the SQLite database
   * context and the the result value of String.
   *
   * @param context Pointer to the SQLite database context.
   * @param val     Result value of an SQL function.
   * @see [http://www.sqlite.org/c3ref/result_blob.html](http://www.sqlite.org/c3ref/result_blob.html)
   */
  abstract fun result_text(context: Long, `val`: String?)

  /**
   * Sets the result of an SQL function as blob data type with the pointer to the SQLite database
   * context and the the result value of byte array.
   *
   * @param context Pointer to the SQLite database context.
   * @param val     Result value of an SQL function.
   * @see [http://www.sqlite.org/c3ref/result_blob.html](http://www.sqlite.org/c3ref/result_blob.html)
   */
  abstract fun result_blob(context: Long, `val`: ByteArray?)

  /**
   * Sets the result of an SQL function as double data type with the pointer to the SQLite
   * database context and the the result value of double.
   *
   * @param context Pointer to the SQLite database context.
   * @param val     Result value of an SQL function.
   * @see [http://www.sqlite.org/c3ref/result_blob.html](http://www.sqlite.org/c3ref/result_blob.html)
   */
  abstract fun result_double(context: Long, `val`: Double)

  /**
   * Sets the result of an SQL function as long data type with the pointer to the SQLite database
   * context and the the result value of long.
   *
   * @param context Pointer to the SQLite database context.
   * @param val     Result value of an SQL function.
   * @see [http://www.sqlite.org/c3ref/result_blob.html](http://www.sqlite.org/c3ref/result_blob.html)
   */
  abstract fun result_long(context: Long, `val`: Long)

  /**
   * Sets the result of an SQL function as int data type with the pointer to the SQLite database
   * context and the the result value of int.
   *
   * @param context Pointer to the SQLite database context.
   * @param val     Result value of an SQL function.
   * @see [http://www.sqlite.org/c3ref/result_blob.html](http://www.sqlite.org/c3ref/result_blob.html)
   */
  abstract fun result_int(context: Long, `val`: Int)

  /**
   * Sets the result of an SQL function as an error with the pointer to the SQLite database
   * context and the error of String.
   *
   * @param context Pointer to the SQLite database context.
   * @param err     Error result of an SQL function.
   * @see [http://www.sqlite.org/c3ref/result_blob.html](http://www.sqlite.org/c3ref/result_blob.html)
   */
  abstract fun result_error(context: Long, err: String?)

  /**
   * @param f   SQLite function object.
   * @param arg Pointer to the parameter of the SQLite function or aggregate.
   * @return Parameter value of the given SQLite function or aggregate in text data type.
   * @see [http://www.sqlite.org/c3ref/value_blob.html](http://www.sqlite.org/c3ref/value_blob.html)
   */
  abstract fun value_text(f: Function, arg: Int): String

  /**
   * @param f   SQLite function object.
   * @param arg Pointer to the parameter of the SQLite function or aggregate.
   * @return Parameter value of the given SQLite function or aggregate in blob data type.
   * @see [http://www.sqlite.org/c3ref/value_blob.html](http://www.sqlite.org/c3ref/value_blob.html)
   */
  abstract fun value_blob(f: Function?, arg: Int): ByteArray?

  /**
   * @param f   SQLite function object.
   * @param arg Pointer to the parameter of the SQLite function or aggregate.
   * @return Parameter value of the given SQLite function or aggregate in double data type
   * @see [http://www.sqlite.org/c3ref/value_blob.html](http://www.sqlite.org/c3ref/value_blob.html)
   */
  abstract fun value_double(f: Function?, arg: Int): Double

  /**
   * @param f   SQLite function object.
   * @param arg Pointer to the parameter of the SQLite function or aggregate.
   * @return Parameter value of the given SQLite function or aggregate in long data type.
   * @see [http://www.sqlite.org/c3ref/value_blob.html](http://www.sqlite.org/c3ref/value_blob.html)
   */
  abstract fun value_long(f: Function?, arg: Int): Long

  /**
   * Accesses the parameter values on the function or aggregate in int data type with the function
   * object and the parameter value.
   *
   * @param f   SQLite function object.
   * @param arg Pointer to the parameter of the SQLite function or aggregate.
   * @return Parameter value of the given SQLite function or aggregate.
   * @see [http://www.sqlite.org/c3ref/value_blob.html](http://www.sqlite.org/c3ref/value_blob.html)
   */
  abstract fun value_int(f: Function?, arg: Int): Int

  /**
   * @param f   SQLite function object.
   * @param arg Pointer to the parameter of the SQLite function or aggregate.
   * @return Parameter datatype of the function or aggregate in int data type.
   * @see [http://www.sqlite.org/c3ref/value_blob.html](http://www.sqlite.org/c3ref/value_blob.html)
   */
  abstract fun value_type(f: Function, arg: Int): Int

  /**
   * Create a user defined function with given function name and the function object.
   *
   * @param name  The function name to be created.
   * @param function     SQLite function object.
   * @param flags Extra flags to use when creating the function, such as [              ][Function.FLAG_DETERMINISTIC]
   * @return [Result Codes](http://www.sqlite.org/c3ref/c_abort.html)
   * @see [http://www.sqlite.org/c3ref/create_function.html](http://www.sqlite.org/c3ref/create_function.html)
   */
  abstract fun create_function(name: String, function: Function, nArgs: Int, flags: Int): Int

  /**
   * De-registers a user defined function
   *
   * @param name Name of the function to de-register.
   * @return [Result Codes](http://www.sqlite.org/c3ref/c_abort.html)
   */
  abstract fun destroy_function(name: String): Int

  /**
   * Create a user defined collation with given collation name and the collation object.
   *
   * @param name The collation name to be created.
   * @param collation    SQLite collation object.
   * @return [Result Codes](https://www.sqlite.org/c3ref/c_abort.html)
   * @see [https://www.sqlite.org/c3ref/create_collation.html](https://www.sqlite.org/c3ref/create_collation.html)
   */
  abstract fun create_collation(name: String, collation: Collation): Int

  /**
   * Create a user defined collation with given collation name and the collation object.
   *
   * @param name The collation name to be created.
   * @return [Result Codes](https://www.sqlite.org/c3ref/c_abort.html)
   */
  abstract fun destroy_collation(name: String): Int

  /**
   * @param dbName       Database name to be backed up.
   * @param destFileName Target backup file name.
   * @param observer     ProgressObserver object.
   * @return [Result Codes](http://www.sqlite.org/c3ref/c_abort.html)
   */
  abstract fun backup(dbName: String, destFileName: String, observer: ProgressObserver?): Int

  /**
   * @param dbName          Database name to be backed up.
   * @param destFileName    Target backup file name.
   * @param observer        ProgressObserver object.
   * @param sleepTimeMillis time to wait during a backup/restore operation if sqlite3_backup_step
   * returns SQLITE_BUSY before continuing
   * @param nTimeouts       the number of times sqlite3_backup_step can return SQLITE_BUSY before
   * failing
   * @param pagesPerStep    the number of pages to copy in each sqlite3_backup_step. If this is
   * negative, the entire DB is copied at once.
   * @return [Result Codes](http://www.sqlite.org/c3ref/c_abort.html)
   */
  abstract fun backup(dbName: String,
                      destFileName: String,
                      observer: ProgressObserver?,
                      sleepTimeMillis: Int,
                      nTimeouts: Int,
                      pagesPerStep: Int): Int

  /**
   * @param dbName         Database name for restoring data.
   * @param sourceFileName Source file name.
   * @param observer       ProgressObserver object.
   * @return [Result Codes](http://www.sqlite.org/c3ref/c_abort.html)
   */
  abstract fun restore(dbName: String, sourceFileName: String, observer: ProgressObserver?): Int

  /**
   * @param dbName          the name of the db to restore
   * @param sourceFileName  the filename of the source db to restore
   * @param observer        ProgressObserver object.
   * @param sleepTimeMillis time to wait during a backup/restore operation if sqlite3_backup_step
   * returns SQLITE_BUSY before continuing
   * @param nTimeouts       the number of times sqlite3_backup_step can return SQLITE_BUSY before
   * failing
   * @param pagesPerStep    the number of pages to copy in each sqlite3_backup_step. If this is
   * negative, the entire DB is copied at once.
   * @return [Result Codes](http://www.sqlite.org/c3ref/c_abort.html)
   */
  abstract fun restore(dbName: String?,
                       sourceFileName: String?,
                       observer: ProgressObserver?,
                       sleepTimeMillis: Int,
                       nTimeouts: Int,
                       pagesPerStep: Int): Int

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

  @Synchronized
  fun addUpdateListener(listener: SQLiteUpdateListener) {
    if (updateListeners.add(listener) && updateListeners.size == 1) {
      set_update_listener(true)
    }
  }

  @Synchronized
  fun addCommitListener(listener: SQLiteCommitListener) {
    if (commitListeners.add(listener) && commitListeners.size == 1) {
      set_commit_listener(true)
    }
  }

  @Synchronized
  fun removeUpdateListener(listener: SQLiteUpdateListener) {
    if (updateListeners.remove(listener) && updateListeners.isEmpty()) {
      set_update_listener(false)
    }
  }

  @Synchronized
  fun removeCommitListener(listener: SQLiteCommitListener) {
    if (commitListeners.remove(listener) && commitListeners.isEmpty()) {
      set_commit_listener(false)
    }
  }

  fun onUpdate(type: Int, database: String?, table: String?, rowId: Long) {
    var listeners: Set<SQLiteUpdateListener>
    synchronized(this) { listeners = HashSet(updateListeners) }
    for (listener in listeners) {
      val operationType = when (type) {
        18 -> SQLiteUpdateListener.Type.INSERT
        9 -> SQLiteUpdateListener.Type.DELETE
        23 -> SQLiteUpdateListener.Type.UPDATE
        else -> throw AssertionError("Unknown type: $type")
      }
      listener.onUpdate(operationType, database, table, rowId)
    }
  }

  fun onCommit(commit: Boolean) {
    var listeners: Set<SQLiteCommitListener>
    synchronized(this) { listeners = HashSet(commitListeners) }
    for (listener in listeners) {
      if (commit) {
        listener.onCommit()
      }
      else {
        listener.onRollback()
      }
    }
  }

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

// https://www.sqlite.org/c3ref/commit_hook.html)
interface SQLiteCommitListener {
  fun onCommit()
  fun onRollback()
}

/** [...](https://www.sqlite.org/c3ref/update_hook.html)  */
interface SQLiteUpdateListener {
  fun onUpdate(type: Type?, database: String?, table: String?, rowId: Long)
  enum class Type {
    INSERT,
    DELETE,
    UPDATE
  }
}

internal object SqliteCodes {
  /** Successful result  */
  const val SQLITE_OK = 0

  /** Library used incorrectly  */
  const val SQLITE_MISUSE = 21

  /** sqlite_step() has another row ready  */
  const val SQLITE_ROW = 100

  /** sqlite_step() has finished executing  */
  const val SQLITE_DONE = 101
}
