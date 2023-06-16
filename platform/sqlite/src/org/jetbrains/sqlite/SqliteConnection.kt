// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RemoveExplicitTypeArguments")

package org.jetbrains.sqlite

import org.intellij.lang.annotations.Language
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val COMMIT = "commit".encodeToByteArray()

class SqliteConnection(file: Path?, config: SQLiteConfig = SQLiteConfig()) : AutoCloseable {
  private val dbRef = AtomicReference<NativeDB?>()
  private val lock = ReentrantLock()

  val isClosed: Boolean
    get() = dbRef.get() == null

  private var currentBusyTimeout: Int

  init {
    file?.parent?.let { Files.createDirectories(it) }
    loadNativeDb()
    val db = NativeDB()
    @Suppress("IfThenToElvis")
    val status = db.open(if (file == null) ":memory:" else file.toAbsolutePath().normalize().toString(), config.openModeFlag) and 0xff
    if (status != SqliteCodes.SQLITE_OK) {
      throw SqliteDb.newException(status, "", null)
    }

    try {
      config.apply(db)
      currentBusyTimeout = config.busyTimeout
    }
    catch (e: Throwable) {
      try {
        db.close()
      }
      catch (closeException: Throwable) {
        e.addSuppressed(closeException)
      }
      throw e
    }

    dbRef.set(db)
  }

  internal inline fun <T> useDb(task: (db: NativeDB) -> T): T {
    lock.withLock {
      return task(getDb())
    }
  }

  fun <T : Binder> prepareStatement(@Language("SQLite") sql: String, binder: T): SqlitePreparedStatement<T> {
    return SqlitePreparedStatement(connection = this, sql = sql.encodeToByteArray(), binder = binder)
  }

  fun <T : Binder> prepareStatement(sqlUtf8: ByteArray, binder: T): SqlitePreparedStatement<T> {
    return SqlitePreparedStatement(connection = this, sql = sqlUtf8, binder = binder)
  }

  fun selectBoolean(@Language("SQLite") sql: String, values: Any? = null): Boolean {
    return executeLifecycle<Boolean>(sql.encodeToByteArray(), values) { db, statementPointer, isEmpty ->
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
    return executeLifecycle<String?>(sql.encodeToByteArray(), values) { db, statementPointer, empty ->
      if (empty) null else db.column_text(statementPointer, 0)
    }
  }

  private inline fun <T> executeLifecycle(sql: ByteArray,
                                          values: Any? = null,
                                          executor: (db: NativeDB, statementPointer: Long, empty: Boolean) -> T): T {
    useDb { db ->
      val statementPointer = db.prepare_utf8(sql)
      try {
        bind(statementPointer = statementPointer, values = values, db = db)
        val isEmpty = step(statementPointer = statementPointer, sql = sql, db = db)
        return executor(db, statementPointer, isEmpty)
      }
      finally {
        db.finalize(statementPointer)
      }
    }
  }

  fun execute(@Language("SQLite") sql: String) {
    getDb().exec(sql.encodeToByteArray())
  }

  fun execute(@Language("SQLite") sql: String, values: Any) {
    executeLifecycle<Unit>(sql.encodeToByteArray(), values) { _, _, _ -> }
  }

  override fun close() {
    lock.withLock {
      dbRef.getAndSet(null)?.close()
    }
  }

  private fun getDb(): NativeDB {
    return requireNotNull(dbRef.get()) { "database connection closed" }
  }

  fun beginTransaction() {
    execute("begin transaction")
  }

  fun commit() {
    getDb().exec(COMMIT)
  }

  fun rollback() {
    getDb().exec("rollback".encodeToByteArray())
  }
}

internal fun step(statementPointer: Long, sql: ByteArray, db: NativeDB): Boolean {
  return when (val status = db.step(statementPointer) and 0xFF) {
    SqliteCodes.SQLITE_DONE -> true
    SqliteCodes.SQLITE_ROW -> false
    else -> throw SqliteDb.newException(status, db.errmsg()!!, sql)
  }
}

private fun bind(statementPointer: Long, values: Any?, db: NativeDB) {
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