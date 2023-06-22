// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.sqlite

import org.intellij.lang.annotations.Language
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class SqlStatementPool<T : Binder> internal constructor(
  @Language("SQLite") sql: String,
  private val connection: SqliteConnection,
  private val binderProducer: () -> T,
) {
  private val pool = ConcurrentLinkedQueue<Pair<SqlitePreparedStatement<T>, T>>()
  private val sql = sql.encodeToByteArray()
  private val size = AtomicInteger()

  fun <R> use(task: (statement: SqlitePreparedStatement<T>, binder: T) -> R): R {
    var item = pool.poll()
    if (item == null) {
      val binder = binderProducer()
      val statement = connection.prepareStatement(sql, binder)
      item = statement to binder
    }
    else {
      size.decrementAndGet()
    }

    val statement = item.first
    val result = try {
      task(statement, item.second)
    }
    catch (e: Throwable) {
      try {
        statement.close()
      }
      catch (closeError: Throwable) {
        e.addSuppressed(closeError)
      }
      throw e
    }

    release(item)
    return result
  }

  private fun release(item: Pair<SqlitePreparedStatement<T>, T>) {
    if (size.get() < 16) {
      pool.add(item)
      size.incrementAndGet()
    }
    else {
      item.first.close()
    }
  }

  internal fun close(db: NativeDB) {
    // prevent adding a new statements
    size.set(Int.MAX_VALUE)
    while (true) {
      val item = pool.poll() ?: break
      item.first.close(db)
    }
  }
}