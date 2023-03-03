// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DuplicatedCode")

package org.jetbrains.sqlite

sealed class Binder {
  internal abstract val paramCount: Int

  abstract val batchQueryCount: Int

  abstract fun addBatch()

  internal abstract fun bindParams(pointer: Long, db: SqliteDb)

  internal abstract fun executeBatch(pointer: Long, db: SqliteDb)

  internal abstract fun clearBatch()
}

object EmptyBinder : Binder() {
  override val paramCount: Int
    get() = 0

  override val batchQueryCount: Int
    get() = 0

  override fun bindParams(pointer: Long, db: SqliteDb) {
  }

  override fun addBatch() = throw IllegalStateException()

  override fun executeBatch(pointer: Long, db: SqliteDb) = throw IllegalStateException()

  override fun clearBatch() {
  }
}

sealed class BaseBinder(override val paramCount: Int) : Binder() {
  @JvmField
  protected var batchPosition = 0

  override var batchQueryCount = 0
    protected set

  override fun clearBatch() {
    batchPosition = 0
    batchQueryCount = 0
  }
}

class ObjectBinder(paramCount: Int, batchCountHint: Int = 1) : BaseBinder(paramCount) {
  private var batch: Array<Any?> = arrayOfNulls(paramCount * batchCountHint)

  override fun bindParams(pointer: Long, db: SqliteDb) {
    assert(batchQueryCount == 0)
    for ((position, value) in batch.withIndex()) {
      sqlBind(pointer, position, value, db)
    }
  }

  override fun toString() = batch.contentToString()

  override fun addBatch() {
    batchPosition += paramCount
    batchQueryCount++
    if ((batchPosition + paramCount) > batch.size) {
      val newBatch = arrayOfNulls<Any?>(batch.size * 2)
      batch.copyInto(newBatch)
      batch = newBatch
    }
  }

  override fun executeBatch(pointer: Long, db: SqliteDb) {
    for (batchIndex in 0 until batchQueryCount) {
      db.reset(pointer)
      for (position in 0 until paramCount) {
        sqlBind(pointer, position, batch[batchIndex * paramCount + position], db)
      }

      stepInBatch(statementPointer = pointer, db = db, batchIndex = batchIndex)
    }
  }

  override fun clearBatch() {
    super.clearBatch()
    batch.fill(null)
  }

  fun bind(vararg values: Any?) {
    assert(values.size == paramCount)
    for (i in 0 until paramCount) {
      batch[batchPosition + i] = values[i]
    }
  }
}