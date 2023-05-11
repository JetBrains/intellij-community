// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RemoveExplicitTypeArguments")

package org.jetbrains.sqlite

import org.intellij.lang.annotations.Language
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

private val COMMIT = "commit".encodeToByteArray()

class SqliteConnection(file: Path?, config: SQLiteConfig = SQLiteConfig()) : AutoCloseable {
  @JvmField
  internal val db: NativeDB

  private val closed = AtomicBoolean(true)

  val isClosed: Boolean
    get() = closed.get()

  private var currentBusyTimeout: Int

  init {
    file?.parent?.let { Files.createDirectories(it) }
    loadNativeDb()
    db = NativeDB()
    @Suppress("IfThenToElvis")
    val status = db.open(if (file == null) ":memory:" else file.toAbsolutePath().normalize().toString(), config.openModeFlag) and 0xff
    if (status != SqliteCodes.SQLITE_OK) {
      throw SqliteDb.newException(status, "", null)
    }
    closed.set(false)

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

  fun <T : Binder> prepareStatement(@Language("SQLite") sql: String, binder: T): SqlitePreparedStatement<T> {
    checkOpen()
    return SqlitePreparedStatement(connection = this, sql = sql.encodeToByteArray(), binder = binder)
  }

  fun <T : Binder> prepareStatement(sqlUtf8: ByteArray, binder: T): SqlitePreparedStatement<T> {
    checkOpen()
    return SqlitePreparedStatement(connection = this, sql = sqlUtf8, binder = binder)
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
  //fun getCurrentTransactionMode(): SQLiteConfig.TransactionMode? {
  //  return field
  //}
  //
  //fun setCurrentTransactionMode(currentTransactionMode: SQLiteConfig.TransactionMode?) {
  //  field = currentTransactionMode
  //}

  fun selectBoolean(@Language("SQLite") sql: String, values: Any? = null): Boolean {
    return executeLifecycle<Boolean>(sql.encodeToByteArray(), values) { statementPointer, isEmpty ->
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
    return executeLifecycle<String?>(sql.encodeToByteArray(), values) { statementPointer, empty ->
      if (empty) null else db.column_text(statementPointer, 0)
    }
  }

  private inline fun <T> executeLifecycle(sql: ByteArray,
                                          values: Any? = null,
                                          executor: (statementPointer: Long, empty: Boolean) -> T): T {
    checkOpen()
    val statementPointer = db.prepare_utf8(sql)
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

  internal fun step(statementPointer: Long, sql: ByteArray): Boolean {
    return when (val status = db.step(statementPointer) and 0xFF) {
      SqliteCodes.SQLITE_DONE -> true
      SqliteCodes.SQLITE_ROW -> false
      else -> throw SqliteDb.newException(status, db.errmsg()!!, sql)
    }
  }

  private fun bind(statementPointer: Long, values: Any?) {
    if (values == null) {
      return
    }

    if (values is Int) {
      val status = db.bind_int(statementPointer, 1, values)
      if (status != SqliteCodes.SQLITE_OK) {
        throw db.newException(status)
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
    db.exec(sql.encodeToByteArray())
  }

  fun execute(@Language("SQLite") sql: String, values: Any) {
    checkOpen()
    executeLifecycle<Unit>(sql.encodeToByteArray(), values) { _, _ -> }
  }

  override fun close() {
    if (closed.compareAndSet(false, true)) {
      db.close()
    }
  }

  private fun checkOpen() {
    check(!closed.get()) { "database connection closed" }
  }

  fun beginTransaction() {
    execute("begin transaction")
  }

  fun commit() {
    checkOpen()
    db.exec(COMMIT)
  }

  fun rollback() {
    checkOpen()
    db.exec("rollback".encodeToByteArray())
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

internal fun sqlBind(pointer: Long, index: Int, v: Any?, db: SqliteDb) {
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

  if (status != SqliteCodes.SQLITE_OK) {
    throw db.newException(status)
  }
}

internal fun stepInBatch(statementPointer: Long, db: SqliteDb, batchIndex: Int) {
  val status = db.step(statementPointer)
  if (status != SqliteCodes.SQLITE_DONE) {
    db.reset(statementPointer)
    if (status == SqliteCodes.SQLITE_ROW) {
      throw IllegalStateException("batch entry $batchIndex: query returns results")
    }
    else {
      throw db.newException(status)
    }
  }
}