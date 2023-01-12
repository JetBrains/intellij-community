// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RemoveExplicitTypeArguments")

package org.jetbrains.sqlite.core

import org.intellij.lang.annotations.Language
import org.jetbrains.sqlite.*
import java.nio.file.Files
import java.nio.file.Path

class SqliteConnection(file: Path?, config: SQLiteConfig = SQLiteConfig()) : AutoCloseable {
  //private final AtomicInteger savePoint = new AtomicInteger(0);
  @JvmField
  internal val db: NativeDB

  private var currentBusyTimeout: Int
  /**
   * @return The busy timeout value for the connection.
   * @see [http://www.sqlite.org/c3ref/busy_timeout.html](http://www.sqlite.org/c3ref/busy_timeout.html)
   */
  var busyTimeout: Int
    get() = currentBusyTimeout
    set(timeoutMillis) {
      currentBusyTimeout = timeoutMillis
      db.busy_timeout(timeoutMillis)
    }

  //
  ///** @see Connection#setSavepoint() */
  //public SqliteSavepoint setSavepoint() throws SQLException {
  //  checkOpen();
  //  var sp = new SqliteSavepoint(savePoint.incrementAndGet());
  //  getDatabase().exec(String.format("SAVEPOINT %s", sp.getSavepointName()));
  //  return sp;
  //}
  //
  ///** @see Connection#setSavepoint(String) */
  //public SqliteSavepoint setSavepoint(String name) throws SQLException {
  //  checkOpen();
  //  var sp = new SqliteSavepoint(savePoint.incrementAndGet(), name);
  //  getDatabase().exec(String.format("SAVEPOINT %s", sp.getSavepointName()));
  //  return sp;
  //}
  //
  ///** @see Connection#releaseSavepoint(Savepoint) */
  //public void releaseSavepoint(Savepoint savepoint) throws SQLException {
  //  checkOpen();
  //  getDatabase()
  //    .exec(String.format("RELEASE SAVEPOINT %s", savepoint.getSavepointName()));
  //}
  //
  ///** @see Connection#rollback(Savepoint) */
  //public void rollback(Savepoint savepoint) throws SQLException {
  //  checkOpen();
  //  getDatabase()
  //    .exec(
  //      String.format("ROLLBACK TO SAVEPOINT %s", savepoint.getSavepointName())
  //    );
  //}

  init {
    file?.parent?.let { Files.createDirectories(it) }
    loadNativeDb()
    db = NativeDB()
    @Suppress("IfThenToElvis")
    db.open(if (file == null) ":memory:" else file.toAbsolutePath().normalize().toString(), config.openModeFlag)
    try {
      config.apply(this)
      currentBusyTimeout = config.busyTimeout
    }
    catch (t: Throwable) {
      try {
        db.close()
      }
      catch (e: Throwable) {
        t.addSuppressed(e)
      }
      throw t
    }
  }

  internal inline fun <T> withConnectionTimeout(queryTimeout: kotlin.time.Duration = kotlin.time.Duration.ZERO, callable: () -> T): T {
    val queryTimeoutInMilliseconds = queryTimeout.inWholeMilliseconds.toInt()
    if (queryTimeoutInMilliseconds <= 0) {
      return callable()
    }

    val origBusyTimeout = busyTimeout
    busyTimeout = queryTimeoutInMilliseconds
    try {
      return callable()
    }
    finally {
      // reset connection timeout to the original value
      busyTimeout = origBusyTimeout
    }
  }

  fun <T : Binder> prepareStatement(@Language("SQLite") sql: String, binder: T): SqlitePreparedStatement<T> {
    checkOpen()
    return SqlitePreparedStatement(connection = this, sql = sql, binder = binder)
  }

  val isClosed: Boolean
    get() = db.isClosed

  //
  ///** @see Connection#setSavepoint() */
  //public SqliteSavepoint setSavepoint() throws SQLException {
  //  checkOpen();
  //  var sp = new SqliteSavepoint(savePoint.incrementAndGet());
  //  getDatabase().exec(String.format("SAVEPOINT %s", sp.getSavepointName()));
  //  return sp;
  //}
  //
  ///** @see Connection#setSavepoint(String) */
  //public SqliteSavepoint setSavepoint(String name) throws SQLException {
  //  checkOpen();
  //  var sp = new SqliteSavepoint(savePoint.incrementAndGet(), name);
  //  getDatabase().exec(String.format("SAVEPOINT %s", sp.getSavepointName()));
  //  return sp;
  //}
  //
  ///** @see Connection#releaseSavepoint(Savepoint) */
  //public void releaseSavepoint(Savepoint savepoint) throws SQLException {
  //  checkOpen();
  //  getDatabase()
  //    .exec(String.format("RELEASE SAVEPOINT %s", savepoint.getSavepointName()));
  //}
  //
  ///** @see Connection#rollback(Savepoint) */
  //public void rollback(Savepoint savepoint) throws SQLException {
  //  checkOpen();
  //  getDatabase()
  //    .exec(
  //      String.format("ROLLBACK TO SAVEPOINT %s", savepoint.getSavepointName())
  //    );
  //}
  //fun getCurrentTransactionMode(): SQLiteConfig.TransactionMode? {
  //  return field
  //}
  //
  //fun setCurrentTransactionMode(currentTransactionMode: SQLiteConfig.TransactionMode?) {
  //  field = currentTransactionMode
  //}

  fun selectBoolean(@Language("SQLite") sql: String, values: Any? = null): Boolean {
    return executeLifecycle<Boolean>(sql, values) { statementPointer, isEmpty ->
      if (isEmpty) {
        false
      }
      else {
        val value = db.column_int(statementPointer, 0)
        value != 0
      }
    }
  }

  fun selectString(@Language("SQLite") sql: String, values: Any? = null): String? {
    return executeLifecycle<String?>(sql, values) { statementPointer, empty ->
      if (empty) null else db.column_text(statementPointer, 0)
    }
  }

  private inline fun <T> executeLifecycle(sql: String,
                                          values: Any? = null,
                                          executor: (statementPointer: Long, empty: Boolean) -> T): T {
    checkOpen()
    val statementPointer = db.prepare_utf8(sql.encodeToByteArray())
    try {
      synchronized(db) {
        bind(statementPointer = statementPointer, values = values)
        val isEmpty = step(statementPointer, sql)
        return executor(statementPointer, isEmpty)
      }
    }
    finally {
      db.finalize(statementPointer)
    }
  }

  internal fun step(statementPointer: Long, sql: String): Boolean {
    return when (val status = db.step(statementPointer) and 0xFF) {
      Codes.SQLITE_DONE -> true
      Codes.SQLITE_ROW -> false
      else -> throw DB.newSQLException(status, db.errmsg(), sql)
    }
  }

  private fun bind(statementPointer: Long, values: Any?) {
    if (values == null) {
      return
    }

    if (values is Int) {
      val status = db.bind_int(statementPointer, 1, values)
      if (status != Codes.SQLITE_OK) {
        throw db.newSQLException(status)
      }
    }
    else {
      @Suppress("UNCHECKED_CAST")
      values as Array<Any?>
      for ((index, value) in values.withIndex()) {
        sqlBind(statementPointer, index, value, db)
      }
    }
  }

  fun execute(@Language("SQLite") sql: String) {
    checkOpen()
    db.exec(sql)
  }

  fun execute(@Language("SQLite") sql: String, values: Any) {
    checkOpen()
    executeLifecycle<Unit>(sql, values) { _, _ -> }
  }

  override fun close() {
    if (!isClosed) {
      db.close()
    }
  }

  private fun checkOpen() {
    check(!isClosed) { "database connection closed" }
  }

  fun beginTransaction() {
    execute("begin transaction")
  }

  fun commit() {
    checkOpen()
    db.exec("commit")
  }

  fun rollback() {
    checkOpen()
    db.exec("rollback")
  }

  ///**
  // * Add a listener for DB update events, see [...](https://www.sqlite.org/c3ref/update_hook.html)
  // *
  // * @param listener The listener to receive update events
  // */
  //fun addUpdateListener(listener: SQLiteUpdateListener?) {
  //  db.addUpdateListener(listener)
  //}

  ///**
  // * Remove a listener registered for DB update events.
  // *
  // * @param listener The listener to no longer receive update events
  // */
  //fun removeUpdateListener(listener: SQLiteUpdateListener?) {
  //  db.removeUpdateListener(listener)
  //}

  ///**
  // * Add a listener for DB commit/rollback events, see
  // * [...](https://www.sqlite.org/c3ref/commit_hook.html)
  // *
  // * @param listener The listener to receive commit events
  // */
  //fun addCommitListener(listener: SQLiteCommitListener?) {
  //  db.addCommitListener(listener)
  //}

  ///**
  // * Remove a listener registered for DB commit/rollback events.
  // *
  // * @param listener The listener is to no longer receive commit/rollback events.
  // */
  //fun removeCommitListener(listener: SQLiteCommitListener?) {
  //  db.removeCommitListener(listener)
  //}
}

internal fun sqlBind(pointer: Long, index: Int, v: Any?, db: DB) {
  val position = index + 1
  val status = when (v) {
    null -> db.bind_null(pointer, position)
    is Int -> db.bind_int(pointer, position, v)
    is Long -> db.bind_long(pointer, position, v)
    is String -> db.bind_text(pointer, position, v)
    is Short -> db.bind_int(pointer, position, v.toInt())
    is Float -> db.bind_double(pointer, position, v.toDouble())
    is Double -> db.bind_double(pointer, position, v)
    is ByteArray -> db.bind_blob(pointer, position, v)
    else -> throw UnsupportedOperationException("Unexpected param type: ${v.javaClass}")
  } and 0xFF

  if (status != Codes.SQLITE_OK) {
    throw db.newSQLException(status)
  }
}

internal fun stepInBatch(statementPointer: Long, db: DB, batchIndex: Int) {
  val status = db.step(statementPointer)
  if (status != DB.SQLITE_DONE) {
    db.reset(statementPointer)
    if (status == DB.SQLITE_ROW) {
      throw IllegalStateException("batch entry $batchIndex: query returns results")
    }
    else {
      throw db.newSQLException(status)
    }
  }
}