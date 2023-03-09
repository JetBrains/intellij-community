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
package org.jetbrains.sqlite.core;

import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.sqlite.*;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * This class is the interface to SQLite. It provides some helper functions
 * used by other parts of the driver. The goal of the helper functions here
 * are not only to provide functionality, but to handle contractual
 * differences between the JDBC specification and the SQLite C API.
 *
 * The process of moving SQLite weirdness into this class is incomplete.
 * You'll still find lots of code in Stmt and PrepStmt that are doing
 * implicit contract conversions. Sorry.
 *
 * The subclass, NativeDB, provides the actual access to SQLite functions.
 */
public abstract class DB implements Codes {
  private final String url;
  private final String fileName;
  private final SQLiteConfig config;
  private final AtomicBoolean closed = new AtomicBoolean(true);
  /** Tracer for statements to avoid unfinalized statements on db close. */
  private final Set<SafeStmtPtr> stmts = ConcurrentHashMap.newKeySet();
  private final Set<SQLiteUpdateListener> updateListeners = new HashSet<>();
  private final Set<SQLiteCommitListener> commitListeners = new HashSet<>();
  /** The "begin;"and "commit;" statement handles. */
  volatile SafeStmtPtr begin;
  volatile SafeStmtPtr commit;

  public DB(String url, String fileName, SQLiteConfig config) {
    this.url = url;
    this.fileName = fileName;
    this.config = config;
  }

  public boolean isClosed() {
    return closed.get();
  }

  public SQLiteConfig getConfig() {
    return config;
  }

  // WRAPPER FUNCTIONS ////////////////////////////////////////////

  /**
   * Aborts any pending operation and returns at its earliest opportunity.
   *
   * @see <a
   * href="http://www.sqlite.org/c3ref/interrupt.html">http://www.sqlite.org/c3ref/interrupt.html</a>
   */
  public abstract void interrupt();

  /**
   * Sets a <a href="http://www.sqlite.org/c3ref/busy_handler.html">busy handler</a> that sleeps
   * for a specified amount of time when a table is locked.
   *
   * @param ms Time to sleep in milliseconds.
   * @see <a
   * href="http://www.sqlite.org/c3ref/busy_timeout.html">http://www.sqlite.org/c3ref/busy_timeout.html</a>
   */
  public abstract void busy_timeout(int ms);

  /**
   * Sets a <a href="http://www.sqlite.org/c3ref/busy_handler.html">busy handler</a> that sleeps
   * for a specified amount of time when a table is locked.
   *
   * @see <a
   * href="http://www.sqlite.org/c3ref/busy_handler.html">http://www.sqlite.org/c3ref/busy_timeout.html</a>
   */
  public abstract void busy_handler(BusyHandler busyHandler);

  /**
   * Return English-language text that describes the error as either UTF-8 or UTF-16.
   *
   * @return Error description in English.
   * @see <a
   * href="http://www.sqlite.org/c3ref/errcode.html">http://www.sqlite.org/c3ref/errcode.html</a>
   */
  abstract String errmsg();

  /**
   * Returns the value for SQLITE_VERSION, SQLITE_VERSION_NUMBER, and SQLITE_SOURCE_ID C
   * preprocessor macros that are associated with the library.
   *
   * @return Compile-time SQLite version information.
   * @see <a
   * href="http://www.sqlite.org/c3ref/libversion.html">http://www.sqlite.org/c3ref/libversion.html</a>
   * @see <a
   * href="http://www.sqlite.org/c3ref/c_source_id.html">http://www.sqlite.org/c3ref/c_source_id.html</a>
   */
  public abstract String libversion();

  /**
   * @return Number of rows that were changed, inserted or deleted by the last SQL statement
   * @see <a
   * href="http://www.sqlite.org/c3ref/changes.html">http://www.sqlite.org/c3ref/changes.html</a>
   */
  public abstract long changes();

  /**
   * @return Number of row changes caused by INSERT, UPDATE or DELETE statements since the
   * database connection was opened.
   * @see <a
   * href="http://www.sqlite.org/c3ref/total_changes.html">http://www.sqlite.org/c3ref/total_changes.html</a>
   */
  public abstract long total_changes();

  /**
   * Enables or disables the sharing of the database cache and schema data structures between
   * connections to the same database.
   *
   * @param enable True to enable; false otherwise.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   * @see <a
   * href="http://www.sqlite.org/c3ref/enable_shared_cache.html">http://www.sqlite.org/c3ref/enable_shared_cache.html</a>
   * @see SQLiteErrorCode
   */
  public abstract int shared_cache(boolean enable);

  /**
   * Enables or disables loading of SQLite extensions.
   *
   * @param enable True to enable; false otherwise.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   * @see <a
   * href="http://www.sqlite.org/c3ref/load_extension.html">http://www.sqlite.org/c3ref/load_extension.html</a>
   */
  public abstract int enable_load_extension(boolean enable);

  /**
   * Execute an SQL statement using the process of compiling, evaluating, and destroying the prepared statement object.
   *
   * @param sql SQL statement to be executed.
   * @see <a href="http://www.sqlite.org/c3ref/exec.html">http://www.sqlite.org/c3ref/exec.html</a>
   */
  public final synchronized void exec(String sql) throws SQLException {
    SafeStmtPtr pointer = prepare(sql);
    try {
      int rc = pointer.safeRunInt(DB::step);
      if (rc != SQLITE_DONE && rc != SQLITE_ROW) {
        throwex(rc);
      }
    }
    finally {
      pointer.close();
    }
  }

  /**
   * Creates an SQLite interface to a database for the given connection.
   *
   * @param file      The database.
   * @param openFlags File opening configurations (<a
   *                  href="http://www.sqlite.org/c3ref/c_open_autoproxy.html">http://www.sqlite.org/c3ref/c_open_autoproxy.html</a>)
   * @see <a
   * href="http://www.sqlite.org/c3ref/open.html">http://www.sqlite.org/c3ref/open.html</a>
   */
  public final synchronized void open(String file, int openFlags) throws SQLException {
    _open(file, openFlags);
    closed.set(false);

    if (fileName.startsWith("file:") && !fileName.contains("cache=")) {
      // URI cache overrides flags
      shared_cache(config.isEnabledSharedCache());
    }
    enable_load_extension(config.isEnabledLoadExtension());
    busy_timeout(config.getBusyTimeout());
  }

  /**
   * Closes a database connection and finalizes any remaining statements before the closing
   * operation.
   *
   * @see <a
   * href="http://www.sqlite.org/c3ref/close.html">http://www.sqlite.org/c3ref/close.html</a>
   */
  public final synchronized void close() throws SQLException {
    // finalize any remaining statements before closing db
    for (SafeStmtPtr element : stmts) {
      element.close();
    }

    // clean-up commit object
    if (begin != null) {
      begin.close();
    }
    if (commit != null) {
      commit.close();
    }

    closed.set(true);
    _close();
  }

  /**
   * Complies an SQL statement.
   *
   * @param stmt The SQL statement to compile.
   * @see <a
   * href="http://www.sqlite.org/c3ref/prepare.html">http://www.sqlite.org/c3ref/prepare.html</a>
   */
  final synchronized void prepare(SqliteStatement stmt, @NotNull String sql) throws SQLException {
    if (stmt.pointer != null) {
      stmt.pointer.close();
    }
    stmt.pointer = prepare(sql);
    final boolean added = stmts.add(stmt.pointer);
    if (!added) {
      throw new IllegalStateException("Already added pointer to statements set");
    }
  }

  /**
   * Destroys a statement.
   *
   * @param safePtr the pointer wrapper to remove from internal structures
   * @param ptr     the raw pointer to free
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   * @throws SQLException if finalization fails
   * @see <a
   * href="http://www.sqlite.org/c3ref/finalize.html">http://www.sqlite.org/c3ref/finalize.html</a>
   */
  public synchronized int finalize(SafeStmtPtr safePtr, long ptr) throws SQLException {
    try {
      return finalize(ptr);
    }
    finally {
      stmts.remove(safePtr);
    }
  }

  /**
   * Creates an SQLite interface to a database with the provided open flags.
   *
   * @param filename  The database to open.
   * @param openFlags File opening configurations (<a
   *                  href="http://www.sqlite.org/c3ref/c_open_autoproxy.html">http://www.sqlite.org/c3ref/c_open_autoproxy.html</a>)
   * @see <a
   * href="http://www.sqlite.org/c3ref/open.html">http://www.sqlite.org/c3ref/open.html</a>
   */
  protected abstract void _open(String filename, int openFlags) throws SQLException;

  /**
   * Closes the SQLite interface to a database.
   *
   * @see <a
   * href="http://www.sqlite.org/c3ref/close.html">http://www.sqlite.org/c3ref/close.html</a>
   */
  protected abstract void _close() throws SQLException;

  /**
   * Complies, evaluates, executes and commits an SQL statement.
   *
   * @param sql An SQL statement.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   * @see <a
   * href="http://www.sqlite.org/c3ref/exec.html">http://www.sqlite.org/c3ref/exec.html</a>
   */
  public abstract int _exec(String sql) throws SQLException;

  /**
   * Complies an SQL statement.
   *
   * @param sql An SQL statement.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   * @see <a
   * href="http://www.sqlite.org/c3ref/prepare.html">http://www.sqlite.org/c3ref/prepare.html</a>
   */
  protected abstract SafeStmtPtr prepare(String sql) throws SQLException;

  /**
   * Destroys a prepared statement.
   *
   * @param stmt Pointer to the statement pointer.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   * @see <a
   * href="http://www.sqlite.org/c3ref/finalize.html">http://www.sqlite.org/c3ref/finalize.html</a>
   */
  protected abstract int finalize(long stmt);

  /**
   * Evaluates a statement.
   *
   * @param stmt Pointer to the statement.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   * @see <a
   * href="http://www.sqlite.org/c3ref/step.html">http://www.sqlite.org/c3ref/step.html</a>
   */
  public abstract int step(long stmt);

  /**
   * Sets a prepared statement object back to its initial state, ready to be re-executed.
   *
   * @param stmt Pointer to the statement.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   * @see <a
   * href="http://www.sqlite.org/c3ref/reset.html">http://www.sqlite.org/c3ref/reset.html</a>
   */
  public abstract int reset(long stmt);

  /**
   * Reset all bindings on a prepared statement (reset all host parameters to NULL).
   *
   * @param stmt Pointer to the statement.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   * @see <a
   * href="http://www.sqlite.org/c3ref/clear_bindings.html">http://www.sqlite.org/c3ref/clear_bindings.html</a>
   */
  public abstract int clear_bindings(long stmt); // TODO remove?

  /**
   * @param stmt Pointer to the statement.
   * @return Number of parameters in a prepared SQL.
   * @see <a
   * href="http://www.sqlite.org/c3ref/bind_parameter_count.html">http://www.sqlite.org/c3ref/bind_parameter_count.html</a>
   */
  public abstract int bind_parameter_count(long stmt);

  /**
   * @param stmt Pointer to the statement.
   * @return Number of columns in the result set returned by the prepared statement.
   * @see <a
   * href="http://www.sqlite.org/c3ref/column_count.html">http://www.sqlite.org/c3ref/column_count.html</a>
   */
  public abstract int column_count(long stmt);

  /**
   * @param stmt Pointer to the statement.
   * @param col  Number of column.
   * @return Datatype code for the initial data type of the result column.
   * @see <a
   * href="http://www.sqlite.org/c3ref/column_blob.html">http://www.sqlite.org/c3ref/column_blob.html</a>
   */
  public abstract int column_type(long stmt, int col);

  /**
   * @param stmt Pointer to the statement.
   * @param col  Number of column.
   * @return Declared type of the table column for prepared statement.
   * @see <a
   * href="http://www.sqlite.org/c3ref/column_decltype.html">http://www.sqlite.org/c3ref/column_decltype.html</a>
   */
  public abstract String column_decltype(long stmt, int col);

  /**
   * @param stmt Pointer to the statement.
   * @param col  Number of column.
   * @return Original text of column name which is the declared in the CREATE TABLE statement.
   * @see <a
   * href="http://www.sqlite.org/c3ref/column_database_name.html">http://www.sqlite.org/c3ref/column_database_name.html</a>
   */
  public abstract String column_table_name(long stmt, int col);

  /**
   * @param stmt Pointer to the statement.
   * @param col  The number of column.
   * @return Name assigned to a particular column in the result set of a SELECT statement.
   * @see <a
   * href="http://www.sqlite.org/c3ref/column_name.html">http://www.sqlite.org/c3ref/column_name.html</a>
   */
  public abstract String column_name(long stmt, int col);

  /**
   * @param stmt Pointer to the statement.
   * @param col  Number of column.
   * @return Value of the column as text data type in the result set of a SELECT statement.
   * @see <a
   * href="http://www.sqlite.org/c3ref/column_blob.html">http://www.sqlite.org/c3ref/column_blob.html</a>
   */
  public abstract String column_text(long stmt, int col);

  /**
   * @param stmt Pointer to the statement.
   * @param col  Number of column.
   * @return BLOB value of the column in the result set of a SELECT statement
   * @see <a
   * href="http://www.sqlite.org/c3ref/column_blob.html">http://www.sqlite.org/c3ref/column_blob.html</a>
   */
  public abstract byte[] column_blob(long stmt, int col);

  /**
   * @param stmt Pointer to the statement.
   * @param col  Number of column.
   * @return DOUBLE value of the column in the result set of a SELECT statement
   * @see <a
   * href="http://www.sqlite.org/c3ref/column_blob.html">http://www.sqlite.org/c3ref/column_blob.html</a>
   */
  public abstract double column_double(long stmt, int col);

  /**
   * @param stmt Pointer to the statement.
   * @param col  Number of column.
   * @return LONG value of the column in the result set of a SELECT statement.
   * @see <a
   * href="http://www.sqlite.org/c3ref/column_blob.html">http://www.sqlite.org/c3ref/column_blob.html</a>
   */
  public abstract long column_long(long stmt, int col);

  /**
   * @param stmt Pointer to the statement.
   * @param col  Number of column.
   * @return INT value of column in the result set of a SELECT statement.
   * @see <a
   * href="http://www.sqlite.org/c3ref/column_blob.html">http://www.sqlite.org/c3ref/column_blob.html</a>
   */
  public abstract int column_int(long stmt, int col);

  /**
   * Binds NULL value to prepared statements with the pointer to the statement object and the
   * index of the SQL parameter to be set to NULL.
   *
   * @param stmt Pointer to the statement.
   * @param pos  The index of the SQL parameter to be set to NULL.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   */
  abstract int bind_null(long stmt, int pos);

  /**
   * Binds int value to prepared statements with the pointer to the statement object, the index of
   * the SQL parameter to be set and the value to bind to the parameter.
   *
   * @param stmt Pointer to the statement.
   * @param pos  The index of the SQL parameter to be set.
   * @param v    Value to bind to the parameter.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   * @see <a
   * href="http://www.sqlite.org/c3ref/bind_blob.html">http://www.sqlite.org/c3ref/bind_blob.html</a>
   */
  abstract int bind_int(long stmt, int pos, int v);

  /**
   * Binds long value to prepared statements with the pointer to the statement object, the index
   * of the SQL parameter to be set and the value to bind to the parameter.
   *
   * @param stmt Pointer to the statement.
   * @param pos  The index of the SQL parameter to be set.
   * @param v    Value to bind to the parameter.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   * @see <a
   * href="http://www.sqlite.org/c3ref/bind_blob.html">http://www.sqlite.org/c3ref/bind_blob.html</a>
   */
  abstract int bind_long(long stmt, int pos, long v);

  /**
   * Binds double value to prepared statements with the pointer to the statement object, the index
   * of the SQL parameter to be set and the value to bind to the parameter.
   *
   * @param stmt Pointer to the statement.
   * @param pos  Index of the SQL parameter to be set.
   * @param v    Value to bind to the parameter.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   * @see <a
   * href="http://www.sqlite.org/c3ref/bind_blob.html">http://www.sqlite.org/c3ref/bind_blob.html</a>
   */
  abstract int bind_double(long stmt, int pos, double v);

  /**
   * Binds text value to prepared statements with the pointer to the statement object, the index
   * of the SQL parameter to be set and the value to bind to the parameter.
   *
   * @param stmt Pointer to the statement.
   * @param pos  Index of the SQL parameter to be set.
   * @param v    value to bind to the parameter.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   * @see <a
   * href="http://www.sqlite.org/c3ref/bind_blob.html">http://www.sqlite.org/c3ref/bind_blob.html</a>
   */
  abstract int bind_text(long stmt, int pos, String v);

  /**
   * Binds blob value to prepared statements with the pointer to the statement object, the index
   * of the SQL parameter to be set and the value to bind to the parameter.
   *
   * @param stmt Pointer to the statement.
   * @param pos  Index of the SQL parameter to be set.
   * @param v    Value to bind to the parameter.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   * @see <a
   * href="http://www.sqlite.org/c3ref/bind_blob.html">http://www.sqlite.org/c3ref/bind_blob.html</a>
   */
  abstract int bind_blob(long stmt, int pos, byte[] v);

  /**
   * Sets the result of an SQL function as NULL with the pointer to the SQLite database context.
   *
   * @param context Pointer to the SQLite database context.
   * @see <a
   * href="http://www.sqlite.org/c3ref/result_blob.html">http://www.sqlite.org/c3ref/result_blob.html</a>
   */
  public abstract void result_null(long context);

  /**
   * Sets the result of an SQL function as text data type with the pointer to the SQLite database
   * context and the the result value of String.
   *
   * @param context Pointer to the SQLite database context.
   * @param val     Result value of an SQL function.
   * @see <a
   * href="http://www.sqlite.org/c3ref/result_blob.html">http://www.sqlite.org/c3ref/result_blob.html</a>
   */
  public abstract void result_text(long context, String val);

  /**
   * Sets the result of an SQL function as blob data type with the pointer to the SQLite database
   * context and the the result value of byte array.
   *
   * @param context Pointer to the SQLite database context.
   * @param val     Result value of an SQL function.
   * @see <a
   * href="http://www.sqlite.org/c3ref/result_blob.html">http://www.sqlite.org/c3ref/result_blob.html</a>
   */
  public abstract void result_blob(long context, byte[] val);

  /**
   * Sets the result of an SQL function as double data type with the pointer to the SQLite
   * database context and the the result value of double.
   *
   * @param context Pointer to the SQLite database context.
   * @param val     Result value of an SQL function.
   * @see <a
   * href="http://www.sqlite.org/c3ref/result_blob.html">http://www.sqlite.org/c3ref/result_blob.html</a>
   */
  public abstract void result_double(long context, double val);

  /**
   * Sets the result of an SQL function as long data type with the pointer to the SQLite database
   * context and the the result value of long.
   *
   * @param context Pointer to the SQLite database context.
   * @param val     Result value of an SQL function.
   * @see <a
   * href="http://www.sqlite.org/c3ref/result_blob.html">http://www.sqlite.org/c3ref/result_blob.html</a>
   */
  public abstract void result_long(long context, long val);

  /**
   * Sets the result of an SQL function as int data type with the pointer to the SQLite database
   * context and the the result value of int.
   *
   * @param context Pointer to the SQLite database context.
   * @param val     Result value of an SQL function.
   * @see <a
   * href="http://www.sqlite.org/c3ref/result_blob.html">http://www.sqlite.org/c3ref/result_blob.html</a>
   */
  public abstract void result_int(long context, int val);

  /**
   * Sets the result of an SQL function as an error with the pointer to the SQLite database
   * context and the the error of String.
   *
   * @param context Pointer to the SQLite database context.
   * @param err     Error result of an SQL function.
   * @see <a
   * href="http://www.sqlite.org/c3ref/result_blob.html">http://www.sqlite.org/c3ref/result_blob.html</a>
   */
  public abstract void result_error(long context, String err);

  /**
   * @param f   SQLite function object.
   * @param arg Pointer to the parameter of the SQLite function or aggregate.
   * @return Parameter value of the given SQLite function or aggregate in text data type.
   * @see <a
   * href="http://www.sqlite.org/c3ref/value_blob.html">http://www.sqlite.org/c3ref/value_blob.html</a>
   */
  public abstract String value_text(Function f, int arg);

  /**
   * @param f   SQLite function object.
   * @param arg Pointer to the parameter of the SQLite function or aggregate.
   * @return Parameter value of the given SQLite function or aggregate in blob data type.
   * @see <a
   * href="http://www.sqlite.org/c3ref/value_blob.html">http://www.sqlite.org/c3ref/value_blob.html</a>
   */
  public abstract byte[] value_blob(Function f, int arg);

  /**
   * @param f   SQLite function object.
   * @param arg Pointer to the parameter of the SQLite function or aggregate.
   * @return Parameter value of the given SQLite function or aggregate in double data type
   * @see <a
   * href="http://www.sqlite.org/c3ref/value_blob.html">http://www.sqlite.org/c3ref/value_blob.html</a>
   */
  public abstract double value_double(Function f, int arg);

  /**
   * @param f   SQLite function object.
   * @param arg Pointer to the parameter of the SQLite function or aggregate.
   * @return Parameter value of the given SQLite function or aggregate in long data type.
   * @see <a
   * href="http://www.sqlite.org/c3ref/value_blob.html">http://www.sqlite.org/c3ref/value_blob.html</a>
   */
  public abstract long value_long(Function f, int arg);

  /**
   * Accesses the parameter values on the function or aggregate in int data type with the function
   * object and the parameter value.
   *
   * @param f   SQLite function object.
   * @param arg Pointer to the parameter of the SQLite function or aggregate.
   * @return Parameter value of the given SQLite function or aggregate.
   * @see <a
   * href="http://www.sqlite.org/c3ref/value_blob.html">http://www.sqlite.org/c3ref/value_blob.html</a>
   */
  public abstract int value_int(Function f, int arg);

  /**
   * @param f   SQLite function object.
   * @param arg Pointer to the parameter of the SQLite function or aggregate.
   * @return Parameter datatype of the function or aggregate in int data type.
   * @see <a
   * href="http://www.sqlite.org/c3ref/value_blob.html">http://www.sqlite.org/c3ref/value_blob.html</a>
   */
  public abstract int value_type(Function f, int arg);

  /**
   * Create a user defined function with given function name and the function object.
   *
   * @param name  The function name to be created.
   * @param f     SQLite function object.
   * @param flags Extra flags to use when creating the function, such as {@link
   *              Function#FLAG_DETERMINISTIC}
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   * @see <a
   * href="http://www.sqlite.org/c3ref/create_function.html">http://www.sqlite.org/c3ref/create_function.html</a>
   */
  public abstract int create_function(String name, Function f, int nArgs, int flags)
    throws SQLException;

  /**
   * De-registers a user defined function
   *
   * @param name Name of the function to de-registered.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   */
  public abstract int destroy_function(String name) throws SQLException;

  /**
   * Create a user defined collation with given collation name and the collation object.
   *
   * @param name The collation name to be created.
   * @param c    SQLite collation object.
   * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   * @see <a
   * href="https://www.sqlite.org/c3ref/create_collation.html">https://www.sqlite.org/c3ref/create_collation.html</a>
   */
  public abstract int create_collation(String name, Collation c) throws SQLException;

  /**
   * Create a user defined collation with given collation name and the collation object.
   *
   * @param name The collation name to be created.
   * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   */
  public abstract int destroy_collation(String name) throws SQLException;

  /**
   * @param dbName       Database name to be backed up.
   * @param destFileName Target backup file name.
   * @param observer     ProgressObserver object.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   */
  public abstract int backup(String dbName, String destFileName, ProgressObserver observer)
    throws SQLException;

  /**
   * @param dbName          Database name to be backed up.
   * @param destFileName    Target backup file name.
   * @param observer        ProgressObserver object.
   * @param sleepTimeMillis time to wait during a backup/restore operation if sqlite3_backup_step
   *                        returns SQLITE_BUSY before continuing
   * @param nTimeouts       the number of times sqlite3_backup_step can return SQLITE_BUSY before
   *                        failing
   * @param pagesPerStep    the number of pages to copy in each sqlite3_backup_step. If this is
   *                        negative, the entire DB is copied at once.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   */
  public abstract int backup(
    String dbName,
    String destFileName,
    ProgressObserver observer,
    int sleepTimeMillis,
    int nTimeouts,
    int pagesPerStep)
    throws SQLException;

  /**
   * @param dbName         Database name for restoring data.
   * @param sourceFileName Source file name.
   * @param observer       ProgressObserver object.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   */
  public abstract int restore(String dbName, String sourceFileName, ProgressObserver observer)
    throws SQLException;

  /**
   * @param dbName          the name of the db to restore
   * @param sourceFileName  the filename of the source db to restore
   * @param observer        ProgressObserver object.
   * @param sleepTimeMillis time to wait during a backup/restore operation if sqlite3_backup_step
   *                        returns SQLITE_BUSY before continuing
   * @param nTimeouts       the number of times sqlite3_backup_step can return SQLITE_BUSY before
   *                        failing
   * @param pagesPerStep    the number of pages to copy in each sqlite3_backup_step. If this is
   *                        negative, the entire DB is copied at once.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   */
  public abstract int restore(
    String dbName,
    String sourceFileName,
    ProgressObserver observer,
    int sleepTimeMillis,
    int nTimeouts,
    int pagesPerStep)
    throws SQLException;

  /**
   * @param id    The id of the limit.
   * @param value The new value of the limit.
   * @return The prior value of the limit
   * @see <a
   * href="https://www.sqlite.org/c3ref/limit.html">https://www.sqlite.org/c3ref/limit.html</a>
   */
  public abstract int limit(int id, int value) throws SQLException;

  /** Progress handler */
  public abstract void register_progress_handler(int vmCalls, ProgressHandler progressHandler) throws SQLException
  ;

  public abstract void clear_progress_handler() throws SQLException;

  /**
   * Returns an array describing the attributes (not null, primary key and auto increment) of
   * columns.
   *
   * @param stmt Pointer to the statement.
   * @return Column attribute array.<br>
   * index[col][0] = true if column constrained NOT NULL;<br>
   * index[col][1] = true if column is part of the primary key; <br>
   * index[col][2] = true if column is auto-increment.
   */
  public abstract boolean[][] column_metadata(long stmt);

  /**
   * Returns an array of column names in the result set of the SELECT statement.
   *
   * @param stmt Stmt object.
   * @return String array of column names.
   */
  public final synchronized String[] column_names(long stmt) {
    String[] names = new String[column_count(stmt)];
    for (int i = 0; i < names.length; i++) {
      names[i] = column_name(stmt, i);
    }
    return names;
  }

  // COMPOUND FUNCTIONS ////////////////////////////////////////////

  /**
   * Bind values to prepared statements
   *
   * @param stmt Pointer to the statement.
   * @param pos  Index of the SQL parameter to be set to NULL.
   * @param v    Value to bind to the parameter.
   * @return <a href="http://www.sqlite.org/c3ref/c_abort.html">Result Codes</a>
   * @see <a
   * href="http://www.sqlite.org/c3ref/bind_blob.html">http://www.sqlite.org/c3ref/bind_blob.html</a>
   */
  final synchronized int sqlbind(long stmt, int pos, Object v) throws SQLException {
    pos++;
    if (v == null) {
      return bind_null(stmt, pos);
    }
    else if (v instanceof Integer) {
      return bind_int(stmt, pos, (Integer)v);
    }
    else if (v instanceof Short) {
      return bind_int(stmt, pos, ((Short)v).intValue());
    }
    else if (v instanceof Long) {
      return bind_long(stmt, pos, (Long)v);
    }
    else if (v instanceof Float) {
      return bind_double(stmt, pos, ((Float)v).doubleValue());
    }
    else if (v instanceof Double) {
      return bind_double(stmt, pos, (Double)v);
    }
    else if (v instanceof String) {
      return bind_text(stmt, pos, (String)v);
    }
    else if (v instanceof byte[]) {
      return bind_blob(stmt, pos, (byte[])v);
    }
    else {
      throw new SQLException("unexpected param type: " + v.getClass());
    }
  }

  /**
   * Submits a batch of commands to the database for execution.
   *
   * @param stmt  Pointer of Stmt object.
   * @param count Number of SQL statements.
   * @param vals  Array of parameter values.
   * @return Array of the number of rows changed or inserted or deleted for each command if all
   * commands execute successfully;
   * @throws SQLException if the statement is not open or is being used elsewhere
   * @see java.sql.Statement#executeBatch()
   */
  synchronized long[] executeBatch(long stmt, int count, Object[] vals, boolean collectChanges) throws SQLException {
    if (count < 1) {
      throw new SQLException("count (" + count + ") < 1");
    }

    final int params = bind_parameter_count(stmt);

    int rc;
    long[] changes = collectChanges ? new long[count] : ArrayUtilRt.EMPTY_LONG_ARRAY;

    for (int i = 0; i < count; i++) {
      reset(stmt);
      for (int j = 0; j < params; j++) {
        rc = sqlbind(stmt, j, vals[(i * params) + j]);
        if (rc != SQLITE_OK) {
          throwex(rc);
        }
      }

      rc = step(stmt);
      if (rc != SQLITE_DONE) {
        reset(stmt);
        if (rc == SQLITE_ROW) {
          throw new BatchUpdateException(
            "batch entry " + i + ": query returns results",
            null,
            0,
            changes,
            null);
        }
        throwex(rc);
      }

      if (collectChanges) {
        changes[i] = changes();
      }
    }

    reset(stmt);
    return changes;
  }

  /**
   * @param statement Stmt object.
   * @param vals      Array of parameter values.
   * @param sql Only for error reporting.
   * @return True if a row of ResultSet is ready; false otherwise.
   * @see <a href="http://www.sqlite.org/c_interface.html#sqlite_exec">http://www.sqlite.org/c_interface.html#sqlite_exec</a>
   */
  public final synchronized boolean execute(SqliteStatement statement, Object[] vals, String sql) throws SQLException {
    int statusCode = statement.pointer.safeRunInt((db, ptr) -> execute(ptr, vals));
    switch (statusCode & 0xFF) {
      case SQLITE_DONE -> {
        return false;
      }
      case SQLITE_ROW -> {
        return true;
      }
      case SQLITE_BUSY, SQLITE_LOCKED, SQLITE_MISUSE, SQLITE_CONSTRAINT -> throw newSQLException(statusCode);
      default -> {
        statement.pointer.close();
        throw newSQLException(statusCode, errmsg(), sql);
      }
    }
  }

  private synchronized int execute(long ptr, Object[] vals) throws SQLException {
    if (vals != null) {
      final int params = bind_parameter_count(ptr);
      if (params > vals.length) {
        throw new SQLException("assertion failure: param count (" + params + ") > value count (" + vals.length + ")");
      }

      for (int i = 0; i < params; i++) {
        int rc = sqlbind(ptr, i, vals[i]);
        if (rc != SQLITE_OK) {
          throwex(rc);
        }
      }
    }

    int statusCode = step(ptr);
    if ((statusCode & 0xFF) == SQLITE_DONE) {
      reset(ptr);
    }
    return statusCode;
  }

  /**
   * Executes the given SQL statement using the one-step query execution interface.
   *
   * @param sql SQL statement to be executed.
   * @return True if a row of ResultSet is ready; false otherwise.
   * @see <a
   * href="http://www.sqlite.org/c3ref/exec.html">http://www.sqlite.org/c3ref/exec.html</a>
   */
  public final synchronized boolean execute(String sql) throws SQLException {
    int statusCode = _exec(sql);
    switch (statusCode) {
      case SQLITE_OK, SQLITE_DONE -> {
        return false;
      }
      case SQLITE_ROW -> {
        return true;
      }
      default -> throw newSQLException(statusCode);
    }
  }

  /**
   * Execute an SQL INSERT, UPDATE or DELETE statement with the Stmt object and an array of
   * parameter values of the SQL statement..
   *
   * @param stmt Stmt object.
   * @param vals Array of parameter values.
   * @return Number of database rows that were changed or inserted or deleted by the most recently
   * completed SQL.
   */
  final synchronized long executeUpdate(SqliteStatement stmt, Object[] vals, boolean collectChanges)
    throws SQLException {
    try {
      if (execute(stmt, vals, null)) {
        throw new SQLException("query returns results");
      }
    }
    finally {
      if (!stmt.pointer.isClosed()) {
        stmt.pointer.safeRunInt(DB::reset);
      }
    }
    return collectChanges ? changes() : -1;
  }

  abstract void set_commit_listener(boolean enabled);

  abstract void set_update_listener(boolean enabled);

  public synchronized void addUpdateListener(SQLiteUpdateListener listener) {
    if (updateListeners.add(listener) && updateListeners.size() == 1) {
      set_update_listener(true);
    }
  }

  public synchronized void addCommitListener(SQLiteCommitListener listener) {
    if (commitListeners.add(listener) && commitListeners.size() == 1) {
      set_commit_listener(true);
    }
  }

  public synchronized void removeUpdateListener(SQLiteUpdateListener listener) {
    if (updateListeners.remove(listener) && updateListeners.isEmpty()) {
      set_update_listener(false);
    }
  }

  public synchronized void removeCommitListener(SQLiteCommitListener listener) {
    if (commitListeners.remove(listener) && commitListeners.isEmpty()) {
      set_commit_listener(false);
    }
  }

  void onUpdate(int type, String database, String table, long rowId) {
    Set<SQLiteUpdateListener> listeners;

    synchronized (this) {
      listeners = new HashSet<>(updateListeners);
    }

    for (SQLiteUpdateListener listener : listeners) {
      SQLiteUpdateListener.Type operationType = switch (type) {
        case 18 -> SQLiteUpdateListener.Type.INSERT;
        case 9 -> SQLiteUpdateListener.Type.DELETE;
        case 23 -> SQLiteUpdateListener.Type.UPDATE;
        default -> throw new AssertionError("Unknown type: " + type);
      };

      listener.onUpdate(operationType, database, table, rowId);
    }
  }

  void onCommit(boolean commit) {
    Set<SQLiteCommitListener> listeners;

    synchronized (this) {
      listeners = new HashSet<>(commitListeners);
    }

    for (SQLiteCommitListener listener : listeners) {
      if (commit) {
        listener.onCommit();
      }
      else {
        listener.onRollback();
      }
    }
  }

  /**
   * Throws SQLException with error message.
   */
  @SuppressWarnings({"unused", "SpellCheckingInspection"})
  final void throwex() throws SQLException {
    throw new SQLException(errmsg());
  }

  /**
   * Throws SQLException with error code.
   *
   * @param errorCode Error code to be passed.
   * @throws SQLException Formatted SQLException with error code.
   */
  @SuppressWarnings("SpellCheckingInspection")
  public final void throwex(int errorCode) throws SQLException {
    throw newSQLException(errorCode);
  }

  /**
   * Throws SQL Exception with error code.
   *
   * @param errorCode Error code to be passed.
   * @return SQLException with error code and message.
   */
  private SQLiteException newSQLException(int errorCode) {
    return newSQLException(errorCode, errmsg(), null);
  }

  /**
   * Throws formatted SQLException with error code and message.
   *
   * @param errorCode    Error code to be passed.
   * @param errorMessage Error message to be passed.
   * @return Formatted SQLException with error code and message.
   */
  public static SQLiteException newSQLException(int errorCode, String errorMessage, @Nullable String sql) {
    SQLiteErrorCode code = SQLiteErrorCode.getErrorCode(errorCode);
    String msg;
    if (code == SQLiteErrorCode.UNKNOWN_ERROR) {
      msg = code + ":" + errorCode + " (" + errorMessage + ")";
    }
    else {
      msg = code + " (" + errorMessage + ")";
    }

    if (sql != null) {
      msg += " (sql=" + sql + ")";
    }
    return new SQLiteException(msg, code);
  }

  public interface ProgressObserver {
    void progress(int remaining, int pageCount);
  }
}
