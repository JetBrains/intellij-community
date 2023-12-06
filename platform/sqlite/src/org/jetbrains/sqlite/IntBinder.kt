// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DuplicatedCode")

package org.jetbrains.sqlite

class IntBinder(paramCount: Int, batchCountHint: Int = 1) : BaseBinder(paramCount) {
  private var batch: IntArray = IntArray(paramCount * batchCountHint)

  fun bind(v1: Int) {
    assert(paramCount == 1)
    batch[batchPosition] = v1
  }

  fun bind(v1: Int, v2: Int) {
    assert(paramCount == 2)
    batch[batchPosition] = v1
    batch[batchPosition + 1] = v2
  }

  fun bind(v1: Int, v2: Int, v3: Int) {
    assert(paramCount == 3)
    batch[batchPosition] = v1
    batch[batchPosition + 1] = v2
    batch[batchPosition + 2] = v3
  }

  internal fun ensureCapacity(count: Int) {
    val expectedSize = count * paramCount
    if (expectedSize > batch.size) {
      val newBatch = IntArray(expectedSize)
      batch.copyInto(newBatch)
      this.batch = newBatch
    }
  }

  override fun bindParams(pointer: Long, db: SqliteDb) {
    assert(batchQueryCount == 0)
    for ((index, value) in batch.withIndex()) {
      val status = db.bind_int(pointer, index + 1, value) and 0xFF
      if (status != SqliteCodes.SQLITE_OK) {
        throw db.newException(status)
      }
    }
  }

  override fun toString() = batch.contentToString()

  override fun addBatch() {
    batchPosition += paramCount
    batchQueryCount++
    if ((batchPosition + paramCount) > batch.size) {
      val newBatch = IntArray(batch.size * 2)
      batch.copyInto(newBatch)
      batch = newBatch
    }
  }

  override fun executeBatch(pointer: Long, db: NativeDB) {
    db.executeBatch(statementPointer = pointer, queryCount = batchQueryCount, paramCount = paramCount, data = batch)
  }
}

class LongBinder(paramCount: Int, batchCountHint: Int = 1) : BaseBinder(paramCount) {
  private var batch = LongArray(paramCount * batchCountHint)

  fun bind(v1: Long) {
    assert(paramCount == 1)
    batch[batchPosition] = v1
  }

  fun bind(v1: Long, v2: Long) {
    assert(paramCount == 2)
    batch[batchPosition] = v1
    batch[batchPosition + 1] = v2
  }

  fun bind(v1: Long, v2: Long, v3: Long) {
    assert(paramCount == 3)
    batch[batchPosition] = v1
    batch[batchPosition + 1] = v2
    batch[batchPosition + 2] = v3
  }

  fun bind(v1: Long, v2: Long, v3: Long, v4: Long) {
    assert(paramCount == 4)
    batch[batchPosition] = v1
    batch[batchPosition + 1] = v2
    batch[batchPosition + 2] = v3
    batch[batchPosition + 3] = v4
  }

  override fun bindParams(pointer: Long, db: SqliteDb) {
    assert(batchQueryCount == 0)
    for ((index, value) in batch.withIndex()) {
      val status = db.bind_long(pointer, index + 1, value) and 0xFF
      if (status != SqliteCodes.SQLITE_OK) {
        throw db.newException(status)
      }
    }
  }

  override fun toString() = batch.contentToString()

  override fun addBatch() {
    batchPosition += paramCount
    batchQueryCount++
    if ((batchPosition + paramCount) > batch.size) {
      val newBatch = LongArray(batch.size * 2)
      batch.copyInto(newBatch)
      batch = newBatch
    }
  }

  override fun executeBatch(pointer: Long, db: NativeDB) {
    for (batchIndex in 0 until batchQueryCount) {
      db.reset(pointer)
      for (index in 0 until paramCount) {
        val status = db.bind_long(pointer, index + 1, batch[batchIndex * paramCount + index]) and 0xFF
        if (status != SqliteCodes.SQLITE_OK) {
          throw db.newException(status)
        }
      }

      stepInBatch(statementPointer = pointer, db = db, batchIndex = batchIndex)
    }
    db.reset(pointer)
  }
}