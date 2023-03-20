/*
 * Copyright (c) 2007 David Crawshaw <david@zentus.com >
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

import java.io.IOException
import java.nio.ByteBuffer

/** This class provides a thin JNI layer over the SQLite3 C API.  */
internal class NativeDB : SqliteDb() {
  // SQLite connection handle.
  private var pointer: Long = 0

  @Suppress("unused")
  private val busyHandler: Long = 0
  @Suppress("unused")
  private val updateListener: Long = 0
  @Suppress("unused")
  private val commitListener: Long = 0

  @Synchronized
  override fun open(file: String, openFlags: Int): Int {
    check(pointer == 0L) { "Database $file is already opened" }
    return open(stringToUtf8ByteArray(file), openFlags)
  }

  @Synchronized
  external override fun open(filename: ByteArray, openFlags: Int): Int

  @Synchronized
  external override fun _close()

  @Synchronized
  override fun _exec(sql: ByteArray): Int {
    return _exec_utf8(sql)
  }

  @Synchronized
  external fun _exec_utf8(sqlUtf8: ByteArray?): Int

  @Synchronized
  external override fun enable_load_extension(enable: Boolean): Int

  external override fun interrupt()

  @Synchronized
  external override fun busy_timeout(ms: Int)

  @Synchronized
  external override fun busy_handler(busyHandler: BusyHandler?)

  @Synchronized
  override fun prepare(sql: ByteArray): SafeStatementPointer {
    return SafeStatementPointer(db = this, pointer = prepare_utf8(sql))
  }

  // byte[] instead of string is actually more performant
  @Synchronized
  external fun prepare_utf8(sqlUtf8: ByteArray?): Long

  @Synchronized
  override fun errmsg(): String? {
    return utf8ByteBufferToString(errmsg_utf8() ?: return null)
  }

  @Suppress("SpellCheckingInspection")
  @Synchronized
  external fun errmsg_utf8(): ByteBuffer?

  @Synchronized
  override fun libversion(): String {
    return utf8ByteBufferToString(libversion_utf8())
  }

  @Suppress("SpellCheckingInspection")
  private external fun libversion_utf8(): ByteBuffer

  @Synchronized
  external override fun changes(): Long

  @Synchronized
  external override fun total_changes(): Long

  @Synchronized
  external override fun finalize(stmt: Long): Int

  @Synchronized
  external override fun step(stmt: Long): Int

  @Synchronized
  external override fun reset(stmt: Long): Int

  @Synchronized
  external override fun clear_bindings(stmt: Long): Int

  @Synchronized
  external override fun bind_parameter_count(stmt: Long): Int

  @Synchronized
  external override fun column_count(stmt: Long): Int

  @Synchronized
  external override fun column_type(stmt: Long, col: Int): Int

  @Synchronized
  override fun column_text(statementPointer: Long, zeroBasedColumnIndex: Int): String? {
    return utf8ByteBufferToString(column_text_utf8(statementPointer, zeroBasedColumnIndex) ?: return null)
  }

  @Synchronized
  external fun column_text_utf8(stmt: Long, col: Int): ByteBuffer?

  @Synchronized
  external override fun column_blob(statementPointer: Long, zeroBasedColumnIndex: Int): ByteArray?

  @Synchronized
  external override fun column_double(statementPointer: Long, zeroBasedColumnIndex: Int): Double

  @Synchronized
  external override fun column_long(statementPointer: Long, zeroBasedColumnIndex: Int): Long

  @Synchronized
  external override fun column_int(statementPointer: Long, zeroBasedColumnIndex: Int): Int

  @Synchronized
  external override fun bind_null(stmt: Long, oneBasedColumnIndex: Int): Int

  @Synchronized
  external override fun bind_int(stmt: Long, oneBasedColumnIndex: Int, v: Int): Int

  @Synchronized
  external override fun bind_long(stmt: Long, oneBasedColumnIndex: Int, v: Long): Int

  @Synchronized
  external override fun bind_double(stmt: Long, oneBasedColumnIndex: Int, v: Double): Int

  @Synchronized
  override fun bind_text(stmt: Long, oneBasedColumnIndex: Int, v: String?): Int {
    return bind_text_utf8(stmt, oneBasedColumnIndex, stringToUtf8ByteArray(v))
  }

  @Synchronized
  external fun bind_text_utf8(stmt: Long, pos: Int, vUtf8: ByteArray?): Int

  @Synchronized
  external override fun bind_blob(stmt: Long, oneBasedColumnIndex: Int, v: ByteArray?): Int

  @Synchronized
  external override fun result_null(context: Long)

  @Synchronized
  override fun result_text(context: Long, `val`: String?) {
    result_text_utf8(context, stringToUtf8ByteArray(`val`))
  }

  @Synchronized
  external fun result_text_utf8(context: Long, valUtf8: ByteArray?)

  @Synchronized
  external override fun result_blob(context: Long, `val`: ByteArray?)

  @Synchronized
  external override fun result_double(context: Long, `val`: Double)

  @Synchronized
  external override fun result_long(context: Long, `val`: Long)

  @Synchronized
  external override fun result_int(context: Long, `val`: Int)

  @Synchronized
  override fun result_error(context: Long, err: String?) {
    result_error_utf8(context, stringToUtf8ByteArray(err))
  }

  @Synchronized
  external fun result_error_utf8(context: Long, errUtf8: ByteArray?)

  @Synchronized
  override fun value_text(f: Function, arg: Int): String {
    return utf8ByteBufferToString(value_text_utf8(f, arg))
  }

  @Synchronized
  external fun value_text_utf8(f: Function?, argUtf8: Int): ByteBuffer

  /** @see SqliteDb.value_blob
   */
  @Synchronized
  external override fun value_blob(f: Function?, arg: Int): ByteArray?

  @Synchronized
  external override fun value_double(f: Function?, arg: Int): Double

  @Synchronized
  external override fun value_long(f: Function?, arg: Int): Long

  @Synchronized
  external override fun value_int(f: Function?, arg: Int): Int

  @Synchronized
  external override fun value_type(f: Function, arg: Int): Int

  /** @see SqliteDb.create_function
   */
  @Synchronized
  override fun create_function(name: String, function: Function, nArgs: Int, flags: Int): Int {
    return create_function_utf8(nameUtf8 = nameToUtf8ByteArray(nameType = "function", name = name),
                                func = function,
                                nArgs = nArgs,
                                flags = flags)
  }

  @Synchronized
  external fun create_function_utf8(nameUtf8: ByteArray, func: Function, nArgs: Int, flags: Int): Int

  @Synchronized
  override fun destroy_function(name: String): Int {
    return destroy_function_utf8(nameToUtf8ByteArray("function", name))
  }

  @Synchronized
  external fun destroy_function_utf8(nameUtf8: ByteArray?): Int

  @Synchronized
  override fun create_collation(name: String, collation: Collation): Int {
    return create_collation_utf8(nameToUtf8ByteArray("collation", name), collation)
  }

  @Synchronized
  external fun create_collation_utf8(nameUtf8: ByteArray, coll: Collation): Int

  @Synchronized
  override fun destroy_collation(name: String): Int {
    return destroy_collation_utf8(nameToUtf8ByteArray(nameType = "collation", name = name))
  }

  @Synchronized
  external fun destroy_collation_utf8(nameUtf8: ByteArray?): Int

  @Synchronized
  external override fun limit(id: Int, value: Int): Int

  override fun backup(dbName: String, destFileName: String, observer: ProgressObserver?): Int {
    return backup(dbNameUtf8 = stringToUtf8ByteArray(dbName),
                  destFileNameUtf8 = stringToUtf8ByteArray(destFileName),
                  observer = observer,
                  sleepTimeMillis = DEFAULT_BACKUP_BUSY_SLEEP_TIME_MILLIS,
                  nTimeouts = DEFAULT_BACKUP_NUM_BUSY_BEFORE_FAIL,
                  pagesPerStep = DEFAULT_PAGES_PER_BACKUP_STEP)
  }

  override fun backup(dbName: String,
                      destFileName: String,
                      observer: ProgressObserver?,
                      sleepTimeMillis: Int,
                      nTimeouts: Int,
                      pagesPerStep: Int): Int {
    return backup(dbNameUtf8 = stringToUtf8ByteArray(dbName),
                  destFileNameUtf8 = stringToUtf8ByteArray(destFileName),
                  observer = observer,
                  sleepTimeMillis = sleepTimeMillis,
                  nTimeouts = nTimeouts,
                  pagesPerStep = pagesPerStep)
  }

  @Synchronized
  external fun backup(dbNameUtf8: ByteArray,
                      destFileNameUtf8: ByteArray,
                      observer: ProgressObserver?,
                      sleepTimeMillis: Int,
                      nTimeouts: Int,
                      pagesPerStep: Int): Int

  @Synchronized
  override fun restore(dbName: String, sourceFileName: String, observer: ProgressObserver?): Int {
    return restore(dbName = dbName,
                   sourceFileName = sourceFileName,
                   observer = observer,
                   sleepTimeMillis = DEFAULT_BACKUP_BUSY_SLEEP_TIME_MILLIS,
                   nTimeouts = DEFAULT_BACKUP_NUM_BUSY_BEFORE_FAIL,
                   pagesPerStep = DEFAULT_PAGES_PER_BACKUP_STEP)
  }

  @Synchronized
  override fun restore(dbName: String?,
                       sourceFileName: String?,
                       observer: ProgressObserver?,
                       sleepTimeMillis: Int,
                       nTimeouts: Int,
                       pagesPerStep: Int): Int {
    return restore(
      dbNameUtf8 = stringToUtf8ByteArray(dbName),
      sourceFileName = stringToUtf8ByteArray(sourceFileName),
      observer = observer,
      sleepTimeMillis = sleepTimeMillis,
      nTimeouts = nTimeouts,
      pagesPerStep = pagesPerStep)
  }

  @Synchronized
  external fun restore(dbNameUtf8: ByteArray?,
                       sourceFileName: ByteArray?,
                       observer: ProgressObserver?,
                       sleepTimeMillis: Int,
                       nTimeouts: Int,
                       pagesPerStep: Int): Int

  @Synchronized
  external override fun set_commit_listener(enabled: Boolean)

  @Synchronized
  external override fun set_update_listener(enabled: Boolean)

  companion object {
    private const val DEFAULT_BACKUP_BUSY_SLEEP_TIME_MILLIS = 100
    private const val DEFAULT_BACKUP_NUM_BUSY_BEFORE_FAIL = 3
    private const val DEFAULT_PAGES_PER_BACKUP_STEP = 100

    private fun nameToUtf8ByteArray(nameType: String, name: String): ByteArray {
      val nameUtf8 = stringToUtf8ByteArray(name)
      check(!(name.isEmpty() || nameUtf8.size > 255)) { "invalid $nameType name: '$name'" }
      return nameUtf8
    }

    /**
     * Throws an IOException. Called from native code
     */
    @Suppress("unused", "SpellCheckingInspection")
    @JvmStatic
    fun throwex(msg: String?) {
      throw IOException(msg)
    }

    // called from native code (only to convert exception text on calling function)
    @JvmStatic
    fun stringToUtf8ByteArray(str: String?): ByteArray {
      return str!!.encodeToByteArray()
    }

    fun utf8ByteBufferToString(buffer: ByteBuffer): String {
      val bytes = ByteArray(buffer.remaining())
      buffer.get(bytes)
      return bytes.decodeToString()
    }
  }
}
