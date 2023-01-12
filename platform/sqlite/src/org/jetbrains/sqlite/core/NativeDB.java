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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.sqlite.BusyHandler;
import org.jetbrains.sqlite.Collation;
import org.jetbrains.sqlite.Function;
import org.jetbrains.sqlite.ProgressHandler;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

/** This class provides a thin JNI layer over the SQLite3 C API. */
public final class NativeDB extends DB {
  private static final int DEFAULT_BACKUP_BUSY_SLEEP_TIME_MILLIS = 100;
  private static final int DEFAULT_BACKUP_NUM_BUSY_BEFORE_FAIL = 3;
  private static final int DEFAULT_PAGES_PER_BACKUP_STEP = 100;

  /** SQLite connection handle. */
  private final long pointer = 0;
  /** busy handler pointer to JNI global busyhandler reference. */
  private final long busyHandler = 0;
  // pointer to commit listener structure, if enabled.
  private final long commitListener = 0;

  // WRAPPER FUNCTIONS ////////////////////////////////////////////
  // pointer to update listener structure, if enabled.
  private final long updateListener = 0;
  /** handler pointer to JNI global progressHandler reference. */
  private long progressHandler;

  public NativeDB() {
    super();
  }

  /** @see DB#_open(String, int) */
  @Override
  protected synchronized void _open(String file, int openFlags) throws SQLException {
    _open_utf8(stringToUtf8ByteArray(file), openFlags);
  }

  synchronized native void _open_utf8(byte[] fileUtf8, int openFlags) throws SQLException;

  /** @see DB#_close() */
  @Override
  protected synchronized native void _close() throws SQLException;

  /** @see DB#_exec(String) */
  @Override
  public synchronized int _exec(String sql) throws SQLException {
    return _exec_utf8(stringToUtf8ByteArray(sql));
  }

  synchronized native int _exec_utf8(byte[] sqlUtf8) throws SQLException;

  /** @see DB#shared_cache(boolean) */
  @Override
  public synchronized native int shared_cache(boolean enable);

  /** @see DB#enable_load_extension(boolean) */
  @Override
  public synchronized native int enable_load_extension(boolean enable);

  /** @see DB#interrupt() */
  @Override
  public native void interrupt();

  /** @see DB#busy_timeout(int) */
  @Override
  public synchronized native void busy_timeout(int ms);

  /** @see DB#busy_handler(BusyHandler) */
  @Override
  public synchronized native void busy_handler(BusyHandler busyHandler);

  /** @see DB#prepare(String) */
  @Override
  protected synchronized SafeStatementPointer prepare(@NotNull String sql) throws SQLException {
    return new SafeStatementPointer(this, prepare_utf8(stringToUtf8ByteArray(sql)));
  }

  // byte[] instead of string is actually more performant
  public synchronized native long prepare_utf8(byte[] sqlUtf8) throws SQLException;

  /** @see DB#errmsg() */
  @Override
  synchronized String errmsg() {
    return utf8ByteBufferToString(errmsg_utf8());
  }

  synchronized native ByteBuffer errmsg_utf8();

  /** @see DB#libversion() */
  @Override
  public synchronized String libversion() {
    return utf8ByteBufferToString(libversion_utf8());
  }

  native ByteBuffer libversion_utf8();

  /** @see DB#changes() */
  @Override
  public synchronized native long changes();

  /** @see DB#total_changes() */
  @Override
  public synchronized native long total_changes();

  /** @see DB#finalize(long) */
  @Override
  public synchronized native int finalize(long stmt);

  /** @see DB#step(long) */
  @Override
  public synchronized native int step(long stmt);

  @Override
  public synchronized native int reset(long stmt);

  /** @see DB#clear_bindings(long) */
  @Override
  public synchronized native int clear_bindings(long stmt);

  /** @see DB#bind_parameter_count(long) */
  @Override
  public synchronized native int bind_parameter_count(long stmt);

  /** @see DB#column_count(long) */
  @Override
  public synchronized native int column_count(long stmt);

  /** @see DB#column_type(long, int) */
  @Override
  public synchronized native int column_type(long stmt, int col);

  /** @see DB#column_decltype(long, int) */
  @Override
  public synchronized String column_decltype(long stmt, int col) {
    return utf8ByteBufferToString(column_decltype_utf8(stmt, col));
  }

  synchronized native ByteBuffer column_decltype_utf8(long stmt, int col);

  /** @see DB#column_table_name(long, int) */
  @Override
  public synchronized String column_table_name(long stmt, int col) {
    return utf8ByteBufferToString(column_table_name_utf8(stmt, col));
  }

  synchronized native ByteBuffer column_table_name_utf8(long stmt, int col);

  /** @see DB#column_name(long, int) */
  @Override
  public synchronized String column_name(long stmt, int col) {
    return utf8ByteBufferToString(column_name_utf8(stmt, col));
  }

  synchronized native ByteBuffer column_name_utf8(long stmt, int col);

  /** @see DB#column_text(long, int) */
  @Override
  public synchronized String column_text(long statementPointer, int zeroBasedColumnIndex) {
    return utf8ByteBufferToString(column_text_utf8(statementPointer, zeroBasedColumnIndex));
  }

  synchronized native ByteBuffer column_text_utf8(long stmt, int col);

  /** @see DB#column_blob(long, int) */
  @Override
  public synchronized native byte[] column_blob(long statementPointer, int zeroBasedColumnIndex);

  /** @see DB#column_double(long, int) */
  @Override
  public synchronized native double column_double(long statementPointer, int zeroBasedColumnIndex);

  /** @see DB#column_long(long, int) */
  @Override
  public synchronized native long column_long(long statementPointer, int zeroBasedColumnIndex);

  /** @see DB#column_int(long, int) */
  @Override
  public synchronized native int column_int(long statementPointer, int zeroBasedColumnIndex);

  /** @see DB#bind_null(long, int) */
  @Override
  synchronized native int bind_null(long stmt, int oneBasedColumnIndex);

  /** @see DB#bind_int(long, int, int) */
  @Override
  public synchronized native int bind_int(long stmt, int oneBasedColumnIndex, int v);

  /** @see DB#bind_long(long, int, long) */
  @Override
  public synchronized native int bind_long(long stmt, int oneBasedColumnIndex, long v);

  /** @see DB#bind_double(long, int, double) */
  @Override
  synchronized native int bind_double(long stmt, int oneBasedColumnIndex, double v);

  /** @see DB#bind_text(long, int, String) */
  @Override
  synchronized int bind_text(long stmt, int oneBasedColumnIndex, String v) {
    return bind_text_utf8(stmt, oneBasedColumnIndex, stringToUtf8ByteArray(v));
  }

  synchronized native int bind_text_utf8(long stmt, int pos, byte[] vUtf8);

  /** @see DB#bind_blob(long, int, byte[]) */
  @Override
  synchronized native int bind_blob(long stmt, int oneBasedColumnIndex, byte[] v);

  /** @see DB#result_null(long) */
  @Override
  public synchronized native void result_null(long context);

  /** @see DB#result_text(long, String) */
  @Override
  public synchronized void result_text(long context, String val) {
    result_text_utf8(context, stringToUtf8ByteArray(val));
  }

  synchronized native void result_text_utf8(long context, byte[] valUtf8);

  /** @see DB#result_blob(long, byte[]) */
  @Override
  public synchronized native void result_blob(long context, byte[] val);

  /** @see DB#result_double(long, double) */
  @Override
  public synchronized native void result_double(long context, double val);

  /** @see DB#result_long(long, long) */
  @Override
  public synchronized native void result_long(long context, long val);

  /** @see DB#result_int(long, int) */
  @Override
  public synchronized native void result_int(long context, int val);

  /** @see DB#result_error(long, String) */
  @Override
  public synchronized void result_error(long context, String err) {
    result_error_utf8(context, stringToUtf8ByteArray(err));
  }

  synchronized native void result_error_utf8(long context, byte[] errUtf8);

  /** @see DB#value_text(Function, int) */
  @Override
  public synchronized String value_text(Function f, int arg) {
    return utf8ByteBufferToString(value_text_utf8(f, arg));
  }

  synchronized native ByteBuffer value_text_utf8(Function f, int argUtf8);

  /** @see DB#value_blob(Function, int) */
  @Override
  public synchronized native byte[] value_blob(Function f, int arg);

  /** @see DB#value_double(Function, int) */
  @Override
  public synchronized native double value_double(Function f, int arg);

  /** @see DB#value_long(Function, int) */
  @Override
  public synchronized native long value_long(Function f, int arg);

  /** @see DB#value_int(Function, int) */
  @Override
  public synchronized native int value_int(Function f, int arg);

  /** @see DB#value_type(Function, int) */
  @Override
  public synchronized native int value_type(Function f, int arg);

  /** @see DB#create_function(String, Function, int, int) */
  @Override
  public synchronized int create_function(String name, Function func, int nArgs, int flags)
    throws SQLException {
    return create_function_utf8(nameToUtf8ByteArray("function", name), func, nArgs, flags);
  }

  synchronized native int create_function_utf8(
    byte[] nameUtf8, Function func, int nArgs, int flags);

  /** @see DB#destroy_function(String) */
  @Override
  public synchronized int destroy_function(String name) throws SQLException {
    return destroy_function_utf8(nameToUtf8ByteArray("function", name));
  }

  synchronized native int destroy_function_utf8(byte[] nameUtf8);

  /** @see DB#create_collation(String, Collation) */
  @Override
  public synchronized int create_collation(String name, Collation coll) throws SQLException {
    return create_collation_utf8(nameToUtf8ByteArray("collation", name), coll);
  }

  synchronized native int create_collation_utf8(byte[] nameUtf8, Collation coll);

  /** @see DB#destroy_collation(String) */
  @Override
  public synchronized int destroy_collation(String name) throws SQLException {
    return destroy_collation_utf8(nameToUtf8ByteArray("collation", name));
  }

  synchronized native int destroy_collation_utf8(byte[] nameUtf8);

  @Override
  public synchronized native int limit(int id, int value) throws SQLException;

  private static byte[] nameToUtf8ByteArray(String nameType, String name) throws SQLException {
    final byte[] nameUtf8 = stringToUtf8ByteArray(name);
    if (name == null || name.isEmpty() || nameUtf8.length > 255) {
      throw new SQLException("invalid " + nameType + " name: '" + name + "'");
    }
    return nameUtf8;
  }

  /**
   * @see DB#backup(String, String,
   * ProgressObserver)
   */
  @Override
  public int backup(String dbName, String destFileName, ProgressObserver observer)
    throws SQLException {
    return backup(
      stringToUtf8ByteArray(dbName),
      stringToUtf8ByteArray(destFileName),
      observer,
      DEFAULT_BACKUP_BUSY_SLEEP_TIME_MILLIS,
      DEFAULT_BACKUP_NUM_BUSY_BEFORE_FAIL,
      DEFAULT_PAGES_PER_BACKUP_STEP);
  }

  /**
   * @see DB#backup(String, String, ProgressObserver, int, int,
   * int)
   */
  @Override
  public int backup(
    String dbName,
    String destFileName,
    ProgressObserver observer,
    int sleepTimeMillis,
    int nTimeouts,
    int pagesPerStep)
    throws SQLException {
    return backup(
      stringToUtf8ByteArray(dbName),
      stringToUtf8ByteArray(destFileName),
      observer,
      sleepTimeMillis,
      nTimeouts,
      pagesPerStep);
  }

  synchronized native int backup(
    byte[] dbNameUtf8,
    byte[] destFileNameUtf8,
    ProgressObserver observer,
    int sleepTimeMillis,
    int nTimeouts,
    int pagesPerStep)
    throws SQLException;

  /**
   * @see DB#restore(String, String,
   * ProgressObserver)
   */
  @Override
  public synchronized int restore(String dbName, String sourceFileName, ProgressObserver observer)
    throws SQLException {

    return restore(
      dbName,
      sourceFileName,
      observer,
      DEFAULT_BACKUP_BUSY_SLEEP_TIME_MILLIS,
      DEFAULT_BACKUP_NUM_BUSY_BEFORE_FAIL,
      DEFAULT_PAGES_PER_BACKUP_STEP);
  }

  // COMPOUND FUNCTIONS (for optimisation) /////////////////////////

  /** @see DB#restore(String, String, ProgressObserver, int, int, int) */
  @Override
  public synchronized int restore(
    String dbName,
    String sourceFileName,
    ProgressObserver observer,
    int sleepTimeMillis,
    int nTimeouts,
    int pagesPerStep)
    throws SQLException {

    return restore(
      stringToUtf8ByteArray(dbName),
      stringToUtf8ByteArray(sourceFileName),
      observer,
      sleepTimeMillis,
      nTimeouts,
      pagesPerStep);
  }

  synchronized native int restore(
    byte[] dbNameUtf8,
    byte[] sourceFileName,
    ProgressObserver observer,
    int sleepTimeMillis,
    int nTimeouts,
    int pagesPerStep)
    throws SQLException;

  /**
   * Provides metadata for table columns.
   *
   * @return For each column returns: <br>
   * res[col][0] = true if column constrained NOT NULL<br>
   * res[col][1] = true if column is part of the primary key<br>
   * res[col][2] = true if column is auto-increment.
   * @see DB#column_metadata(long)
   */
  @Override
  public synchronized native boolean[][] column_metadata(long stmt);

  @Override
  synchronized native void set_commit_listener(boolean enabled);

  @Override
  synchronized native void set_update_listener(boolean enabled);

  @Override
  public synchronized native void register_progress_handler(
    int vmCalls, ProgressHandler progressHandler) throws SQLException;

  @Override
  public synchronized native void clear_progress_handler() throws SQLException;

  /**
   * Getter for native pointer to validate memory is properly cleaned up in unit tests
   *
   * @return a native pointer to validate memory is properly cleaned up in unit tests
   */
  long getBusyHandler() {
    return busyHandler;
  }

  /**
   * Getter for native pointer to validate memory is properly cleaned up in unit tests
   *
   * @return a native pointer to validate memory is properly cleaned up in unit tests
   */
  long getCommitListener() {
    return commitListener;
  }

  /**
   * Getter for native pointer to validate memory is properly cleaned up in unit tests
   *
   * @return a native pointer to validate memory is properly cleaned up in unit tests
   */
  long getUpdateListener() {
    return updateListener;
  }

  /**
   * Getter for native pointer to validate memory is properly cleaned up in unit tests
   *
   * @return a native pointer to validate memory is properly cleaned up in unit tests
   */
  long getProgressHandler() {
    return progressHandler;
  }

  /**
   * Throws an SQLException. Called from native code
   *
   * @param msg Message for the SQLException.
   * @throws SQLException the generated SQLException
   */
  @SuppressWarnings("unused")
  static void throwex(String msg) throws SQLException {
    throw new SQLException(msg);
  }

  static byte[] stringToUtf8ByteArray(String str) {
    return str.getBytes(StandardCharsets.UTF_8);
  }

  static String utf8ByteBufferToString(ByteBuffer buffer) {
    if (buffer == null) {
      return null;
    }
    byte[] buff = new byte[buffer.remaining()];
    buffer.get(buff);
    return new String(buff, StandardCharsets.UTF_8);
  }
}
