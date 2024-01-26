// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DuplicatedCode")

package org.jetbrains.sqlite

sealed class Binder {
  internal abstract val paramCount: Int

  abstract val batchQueryCount: Int

  abstract fun addBatch()

  internal abstract fun bindParams(pointer: Long, db: SqliteDb)

  internal abstract fun executeBatch(pointer: Long, db: NativeDB)

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

  override fun executeBatch(pointer: Long, db: NativeDB) = throw IllegalStateException()

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

  override fun executeBatch(pointer: Long, db: NativeDB) {
    for (batchIndex in 0 until batchQueryCount) {
      db.reset(pointer)
      for (position in 0 until paramCount) {
        sqlBind(pointer, position, batch[batchIndex * paramCount + position], db)
      }

      stepInBatch(statementPointer = pointer, db = db, batchIndex = batchIndex)
    }
    db.reset(pointer)
  }

  override fun clearBatch() {
    super.clearBatch()
    batch.fill(null)
  }

  fun bind(v1: Any?) {
    assert(paramCount == 1)
    batch[batchPosition] = v1
  }

  fun bind(v1: Any?, v2: Any?) {
    assert(paramCount == 2)
    batch[batchPosition] = v1
    batch[batchPosition + 1] = v2
  }

  fun bind(v1: Any?, v2: Any?, v3: Any?) {
    assert(paramCount == 3)
    batch[batchPosition] = v1
    batch[batchPosition + 1] = v2
    batch[batchPosition + 2] = v3
  }

  fun bind(v1: Any?, v2: Any?, v3: Any?, v4: Any?) {
    assert(paramCount == 4)
    batch[batchPosition] = v1
    batch[batchPosition + 1] = v2
    batch[batchPosition + 2] = v3
    batch[batchPosition + 3] = v4
  }

  fun bind(v1: Any?, v2: Any?, v3: Any?, v4: Any?, v5: Any?) {
    assert(paramCount == 5)
    batch[batchPosition] = v1
    batch[batchPosition + 1] = v2
    batch[batchPosition + 2] = v3
    batch[batchPosition + 3] = v4
    batch[batchPosition + 4] = v5
  }

  fun bind(v1: Any?, v2: Any?, v3: Any?, v4: Any?, v5: Any?, v6: Any?) {
    assert(paramCount == 6)
    batch[batchPosition] = v1
    batch[batchPosition + 1] = v2
    batch[batchPosition + 2] = v3
    batch[batchPosition + 3] = v4
    batch[batchPosition + 4] = v5
    batch[batchPosition + 5] = v6
  }

  fun bindMultiple(vararg values: Any?) {
    assert(values.size == paramCount)
    System.arraycopy(values, 0, batch, batchPosition, paramCount)
  }

  fun bind(v1: Any?, v2: Any?, v3: Any?, v4: Any?, v5: Any?, v6: Any?, v7: Any?) {
    assert(paramCount == 7)
    batch[batchPosition] = v1
    batch[batchPosition + 1] = v2
    batch[batchPosition + 2] = v3
    batch[batchPosition + 3] = v4
    batch[batchPosition + 4] = v5
    batch[batchPosition + 5] = v6
    batch[batchPosition + 6] = v7
  }
}

// Type-safe binders
sealed class MyObjectBinder(paramCount: Int, batchCountHint: Int) : Binder() {
  protected val myBinder = ObjectBinder(paramCount, batchCountHint)

  override val paramCount: Int
    get() = myBinder.paramCount
  override val batchQueryCount: Int
    get() = myBinder.batchQueryCount

  override fun addBatch() {
    myBinder.addBatch()
  }

  override fun bindParams(pointer: Long, db: SqliteDb) {
    myBinder.bindParams(pointer, db)
  }

  override fun executeBatch(pointer: Long, db: NativeDB) {
    myBinder.executeBatch(pointer, db)
  }

  override fun clearBatch() {
    myBinder.clearBatch()
  }
}

object ObjectBinderFactory {
  inline fun <reified T1> create1(batchCountHint: Int = 1): ObjectBinder1<T1> {
    verifyTypes(T1::class.java)

    return ObjectBinder1(batchCountHint)
  }

  inline fun <reified T1, reified T2> create2(batchCountHint: Int = 1): ObjectBinder2<T1, T2> {
    verifyTypes(T1::class.java, T2::class.java)

    return ObjectBinder2(batchCountHint)
  }

  inline fun <reified T1, reified T2, reified T3> create3(batchCountHint: Int = 1): ObjectBinder3<T1, T2, T3> {
    verifyTypes(T1::class.java, T2::class.java, T3::class.java)

    return ObjectBinder3(batchCountHint)
  }

  inline fun <reified T1, reified T2, reified T3, reified T4> create4(batchCountHint: Int = 1): ObjectBinder4<T1, T2, T3, T4> {
    verifyTypes(T1::class.java, T2::class.java, T3::class.java, T4::class.java)

    return ObjectBinder4(batchCountHint)
  }

  inline fun <reified T1, reified T2, reified T3, reified T4, reified T5> create5(batchCountHint: Int = 1): ObjectBinder5<T1, T2, T3, T4, T5> {
    verifyTypes(T1::class.java, T2::class.java, T3::class.java, T4::class.java, T5::class.java)

    return ObjectBinder5(batchCountHint)
  }

  inline fun <reified T1, reified T2, reified T3, reified T4, reified T5, reified T6> create6(batchCountHint: Int = 1): ObjectBinder6<T1, T2, T3, T4, T5, T6> {
    verifyTypes(T1::class.java, T2::class.java, T3::class.java, T4::class.java, T5::class.java, T6::class.java)

    return ObjectBinder6(batchCountHint)
  }

  fun verifyTypes(vararg types: Class<*>) {
    for (type in types) {
      if (!SQLITE_ALLOWED_TYPES.contains(type)) {
        error("Tried to create a binder with type $type which is not supported by binder")
      }
    }
  }
}

class ObjectBinder1<T1>(batchCountHint: Int) : MyObjectBinder(1, batchCountHint) {
  fun bind(v1: T1) = myBinder.bind(v1)
}

class ObjectBinder2<T1, T2>(batchCountHint: Int) : MyObjectBinder(2, batchCountHint) {
  fun bind(v1: T1, v2: T2) = myBinder.bind(v1, v2)
}

class ObjectBinder3<T1, T2, T3>(batchCountHint: Int) : MyObjectBinder(3, batchCountHint) {
  fun bind(v1: T1, v2: T2, v3: T3) = myBinder.bind(v1, v2, v3)
}

class ObjectBinder4<T1, T2, T3, T4>(batchCountHint: Int) : MyObjectBinder(4, batchCountHint) {
  fun bind(v1: T1, v2: T2, v3: T3, v4: T4) = myBinder.bind(v1, v2, v3, v4)
}

class ObjectBinder5<T1, T2, T3, T4, T5>(batchCountHint: Int) : MyObjectBinder(5, batchCountHint) {
  fun bind(v1: T1, v2: T2, v3: T3, v4: T4, v5: T5) = myBinder.bind(v1, v2, v3, v4, v5)
}

class ObjectBinder6<T1, T2, T3, T4, T5, T6>(batchCountHint: Int) : MyObjectBinder(6, batchCountHint) {
  fun bind(v1: T1, v2: T2, v3: T3, v4: T4, v5: T5, v6: T6) = myBinder.bind(v1, v2, v3, v4, v5, v6)
}