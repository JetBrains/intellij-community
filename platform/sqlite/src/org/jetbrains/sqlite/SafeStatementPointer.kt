// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.sqlite

/**
 * A class for safely wrapping calls to a native pointer to a statement, ensuring no other thread
 * has access to the pointer while it is run
 */
internal class SafeStatementPointer(private val connection: SqliteConnection, @JvmField internal val pointer: Long) {
  /**
   * Check whether this pointer has been closed
   */
  @Volatile
  var isClosed: Boolean = false
    private set

  internal fun close(db: SqliteDb) {
    if (isClosed) {
      return
    }

    try {
      val status = db.finalize(this, pointer)
      if (status != SqliteCodes.SQLITE_OK && status != SqliteCodes.SQLITE_MISUSE) {
        throw db.newException(status)
      }
    }
    finally {
      isClosed = true
    }
  }

  /**
   * Run a callback with the wrapped pointer safely.
   *
   * @param task the function to run
   * @return the return of the passed in function
   */
  inline fun safeRunInt(task: (db: NativeDB, statementPointer: Long) -> Int): Int {
    connection.useDb { db ->
      ensureOpen()
      return task(db, pointer)
    }
  }

  /**
   * Run a callback with the wrapped pointer safely.
   *
   * @param task the function to run
   * @return the return code of the function
   */
  inline fun <T> safeRun(task: (db: SqliteDb, statementPointer: Long) -> T): T {
    connection.useDb { db ->
      ensureOpen()
      return task(db, pointer)
    }
  }

  fun ensureOpen() {
    check(!isClosed) { "The statement pointer is closed" }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val that = other as SafeStatementPointer
    return pointer == that.pointer
  }

  override fun hashCode(): Int = pointer.hashCode()
}