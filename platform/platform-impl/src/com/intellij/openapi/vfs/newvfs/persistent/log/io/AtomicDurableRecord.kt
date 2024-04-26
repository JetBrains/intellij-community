// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.io

import com.intellij.openapi.vfs.newvfs.persistent.log.io.DurablePersistentByteArray.Companion.IncompatibleLayoutException
import com.intellij.openapi.vfs.newvfs.persistent.log.io.DurablePersistentByteArray.Companion.OpenMode
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface AtomicDurableRecord<R : Any> : AutoCloseable {
  /**
   * Returns an immutable view on the state of a record [R],
   * i.e., an attempt to modify fields of such a view will result in [IllegalAccessError].
   */
  fun get(): R

  /**
   * Atomically updates the state of the record [R] with the durability guarantee provided by [DurablePersistentByteArray].
   * @param upd sets the fields of the record to new desired values
   * @return an [immutable view][get] on the resulting state of the record
   */
  fun update(upd: R.() -> Unit): R

  companion object {
    /**
     * Changes to the size of the [scheme][recordBuilder] of the record [R] will result in [IncompatibleLayoutException] being thrown.
     * Changing the order of the fields may result in fields of the record having incorrect values. Use with care, reserve space beforehand.
     */
    @Throws(IncompatibleLayoutException::class)
    fun <R : Any> open(
      path: Path,
      mode: OpenMode,
      recordBuilder: RecordBuilder<R>.() -> R
    ): AtomicDurableRecord<R> {
      val schemeCalculator = RecordSchemeCalculator<R>()
      schemeCalculator.recordBuilder()
      val size = schemeCalculator.size
      val durableStateHolder = DurablePersistentByteArray.open(path, mode, size) { schemeCalculator.defaultByteArray }
      return AtomicDurableRecordImpl(durableStateHolder, recordBuilder)
    }

    private class AtomicDurableRecordImpl<R : Any>(
      val durableStateHolder: DurablePersistentByteArray,
      val recordBuilder: RecordBuilder<R>.() -> R,
    ) : AtomicDurableRecord<R> {
      override fun get(): R {
        val state = durableStateHolder.getLastSnapshot()
        return ImmutableRecordAccessor<R>(state).recordBuilder()
      }

      override fun update(upd: R.() -> Unit): R {
        val finalState = durableStateHolder.commitChange { modify ->
          val mutableRecord = MutableRecordAccessor<R>(modify).recordBuilder()
          mutableRecord.upd()
        }
        return ImmutableRecordAccessor<R>(finalState).recordBuilder()
      }

      override fun close() {
        durableStateHolder.close()
      }
    }

    interface RecordBuilder<R : Any> {
      fun <T : Any> custom(bytes: Int,
                           default: T,
                           serialize: T.(ByteBuffer) -> Unit,
                           deserialize: (ByteBuffer) -> T): ReadWriteProperty<R, T>

      /**
       * size = 4 bytes
       */
      fun int(default: Int = 0): ReadWriteProperty<R, Int> =
        custom(Int.SIZE_BYTES, default, serialize = { it.putInt(this) }, deserialize = { it.getInt() })

      /**
       * size = 8 bytes
       */
      fun long(default: Long = 0L): ReadWriteProperty<R, Long> =
        custom(Long.SIZE_BYTES, default, serialize = { it.putLong(this) }, deserialize = { it.getLong() })

      fun bytearray(size: Int, default: () -> ByteArray = { ByteArray(size) }): ReadWriteProperty<R, ByteArray> =
        custom(size, default(), serialize = { it.put(this) }, deserialize = {
          val result = ByteArray(size)
          it.get(result)
          result
        })

      /**
       * size = 1 byte
       */
      fun boolean(default: Boolean): ReadWriteProperty<R, Boolean> =
        custom(1, default, serialize = { it.put(if (this) 1.toByte() else 0.toByte()) }, deserialize = {
          val b = it.get()
          assert(b == 0.toByte() || b == 1.toByte())
          b != 0.toByte()
        })
    }

    private class RecordSchemeCalculator<R : Any> : RecordBuilder<R> {
      private val defaultByteArrayStream = ByteArrayOutputStream()
      val defaultByteArray: ByteArray get() = defaultByteArrayStream.toByteArray()
      val size: Int get() = defaultByteArrayStream.size()

      override fun <T : Any> custom(bytes: Int,
                                    default: T,
                                    serialize: T.(ByteBuffer) -> Unit,
                                    deserialize: (ByteBuffer) -> T): ReadWriteProperty<R, T> {
        val buf = ByteBuffer.allocate(bytes)
        default.serialize(buf)
        check(buf.remaining() == 0) { "serialize of $default didn't fill all $bytes bytes" }
        defaultByteArrayStream.writeBytes(buf.array())
        return object : ReadWriteProperty<R, T> {
          override fun getValue(thisRef: R, property: KProperty<*>): T = throw AssertionError("should not be accessed")
          override fun setValue(thisRef: R, property: KProperty<*>, value: T) = throw AssertionError("should not be accessed")
        }
      }
    }

    private class ImmutableRecordAccessor<R : Any>(val state: ByteArray) : RecordBuilder<R> {
      var offset = 0
      override fun <T : Any> custom(bytes: Int,
                                    default: T,
                                    serialize: T.(ByteBuffer) -> Unit,
                                    deserialize: (ByteBuffer) -> T): ReadWriteProperty<R, T> {
        require(bytes > 0 && offset >= 0 && offset + bytes <= state.size)
        val fieldOffset = offset
        offset += bytes
        return object : ReadWriteProperty<R, T> {
          override fun getValue(thisRef: R, property: KProperty<*>): T {
            val buf = ByteBuffer.wrap(state, fieldOffset, bytes)
            return deserialize(buf)
          }

          override fun setValue(thisRef: R, property: KProperty<*>, value: T) {
            throw IllegalAccessError("setting fields of the record obtained through get() is prohibited. Use update()")
          }
        }
      }
    }

    private class MutableRecordAccessor<R : Any>(val mutableState: ByteArray) : RecordBuilder<R> {
      var offset = 0
      override fun <T : Any> custom(bytes: Int,
                                    default: T,
                                    serialize: T.(ByteBuffer) -> Unit,
                                    deserialize: (ByteBuffer) -> T): ReadWriteProperty<R, T> {
        require(bytes > 0 && offset >= 0 && offset + bytes <= mutableState.size)
        val fieldOffset = offset
        offset += bytes
        return object : ReadWriteProperty<R, T> {
          override fun getValue(thisRef: R, property: KProperty<*>): T {
            val buf = ByteBuffer.wrap(mutableState, fieldOffset, bytes)
            return deserialize(buf)
          }

          override fun setValue(thisRef: R, property: KProperty<*>, value: T) {
            val buf = ByteBuffer.wrap(mutableState, fieldOffset, bytes)
            value.serialize(buf)
          }
        }
      }
    }
  }
}