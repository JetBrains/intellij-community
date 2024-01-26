// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RemoveExplicitTypeArguments")

package org.jetbrains.sqlite

import org.intellij.lang.annotations.Language
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val BEGIN_TRANSACTION = "begin transaction".encodeToByteArray()
private val COMMIT = "commit".encodeToByteArray()
private val ROLLBACK = "rollback".encodeToByteArray()

private val savepointNameGenerator = AtomicLong()

class SqliteConnection(file: Path?, readOnly: Boolean = false) : AutoCloseable {
  private val dbRef = AtomicReference<NativeDB?>()
  private val lock = ReentrantLock()

  val isClosed: Boolean
    get() = dbRef.get() == null

  private val statementPoolList = mutableListOf<SqlStatementPool<*>>()

  init {
    file?.parent?.let { Files.createDirectories(it) }
    loadNativeDb()
    val db = NativeDB()
    val filePath = file?.toAbsolutePath()?.normalize()?.toString()
    @Suppress("IfThenToElvis")
    val status = db.open(if (filePath == null) ":memory:" else filePath,
                         if (readOnly) SQLiteOpenMode.READONLY.flag else (SQLiteOpenMode.READWRITE.flag or SQLiteOpenMode.CREATE.flag)) and 0xff
    if (status != SqliteCodes.SQLITE_OK) {
      throw newException(status, filePath.orEmpty(), null)
    }

    try {
      SQLiteConfig().apply(db)
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

  fun <T : Binder> statementPool(@Language("SQLite") sql: String, binderProducer: () -> T): SqlStatementPool<T> {
    return lock.withLock {
      val pool = SqlStatementPool(sql = sql, connection = this, binderProducer = binderProducer)
      statementPoolList.add(pool)
      pool
    }
  }

  internal inline fun <T> useDb(task: (db: NativeDB) -> T): T {
    if (dbRef.get() == null) {
      throw AlreadyClosedException()
    }

    lock.withLock {
      return task(getDb())
    }
  }

  private fun getDb(): NativeDB {
    return dbRef.get() ?: throw AlreadyClosedException()
  }

  // https://www.sqlite.org/lang_savepoint.html
  fun <T> withSavePoint(task: () -> T): T {
    // incremental prefix for easier debug
    // use prefix `p` as name cannot start with a number
    val name = "p" + savepointNameGenerator.getAndIncrement().toString() +
               "_" +
               java.lang.Long.toUnsignedString(System.currentTimeMillis(), Character.MAX_RADIX)
    useDb { it.exec("savepoint $name".toByteArray()) }
    var ok = false
    try {
      val result = task()
      ok = true
      return result
    }
    finally {
      if (ok) {
        useDb { it.exec("release savepoint $name".toByteArray()) }
      }
      else {
        useDb { it.exec("rollback transaction to savepoint $name".toByteArray()) }
      }
    }
  }

  fun <T : Binder> prepareStatement(@Language("SQLite") sql: String, binder: T): SqlitePreparedStatement<T> {
    return prepareStatement(sqlUtf8 = sql.encodeToByteArray(), binder = binder)
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

  fun selectInt(@Language("SQLite") sql: String, values: Any? = null): Int? {
    return executeLifecycle<Int?>(sql.encodeToByteArray(), values) { db, statementPointer, empty ->
      if (empty) null else db.column_int(statementPointer, 0)
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
    useDb { it.exec(sql.encodeToByteArray()) }
  }

  fun execute(@Language("SQLite") sql: String, values: Any) {
    executeLifecycle<Unit>(sql.encodeToByteArray(), values) { _, _, _ -> }
  }

  fun affectedRows(): Int {
    return selectInt("select changes()") ?: 0
  }

  fun interruptAndClose() {
    val db = dbRef.getAndSet(null) ?: return
    // not under lock - as we currently may hold the lock in another thread
    db.interrupt()
    lock.withLock {
      doClose(db)
    }
  }

  override fun close() {
    val db = dbRef.getAndSet(null) ?: return
    lock.withLock {
      doClose(db)
    }
  }

  private fun doClose(db: NativeDB) {
    val pool = statementPoolList.toList()
    statementPoolList.clear()

    var error: Throwable? = null
    for (item in pool) {
      try {
        item.close(db)
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

    try {
      db.close()
    }
    catch (e: Throwable) {
      if (error == null) {
        error = e
      }
      else {
        error.addSuppressed(e)
      }
    }

    error?.let { throw it }
  }

  fun beginTransaction() {
    useDb { it.exec(BEGIN_TRANSACTION) }
  }

  fun commit() {
    useDb { it.exec(COMMIT) }
  }

  fun rollback() {
    useDb { it.exec(ROLLBACK) }
  }
}

internal fun step(statementPointer: Long, sql: ByteArray, db: NativeDB): Boolean {
  return when (val status = db.step(statementPointer) and 0xFF) {
    SqliteCodes.SQLITE_DONE -> true
    SqliteCodes.SQLITE_ROW -> false
    else -> throw newException(status, db.errmsg()!!, sql)
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

val SQLITE_ALLOWED_TYPES = setOf(
  Int::class.java,
  java.lang.Integer::class.java,
  Long::class.java,
  String::class.java,
  Short::class.java,
  Float::class.java,
  Double::class.java,
  ByteArray::class.java
)

internal fun stepInBatch(statementPointer: Long, db: NativeDB, batchIndex: Int) {
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

class AlreadyClosedException : CancellationException()

