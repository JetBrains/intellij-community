// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

import com.intellij.openapi.vfs.newvfs.AttributeInputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordAccessor
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.bind
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.fmap
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.mapCases
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.DefinedState

interface VfsSnapshot {
  val point: () -> OperationLogStorage.Iterator

  fun getFileById(fileId: Int): VirtualFileSnapshot

  interface VirtualFileSnapshot {
    val fileId: Int

    val nameId: Property<Int>
    val parentId: Property<Int>
    val length: Property<Long>
    val timestamp: Property<Long>
    val flags: Property<@PersistentFS.Attributes Int>
    val contentRecordId: Property<Int>
    val attributesRecordId: Property<Int>

    val name: Property<String>
    val parent: Property<VirtualFileSnapshot?>

    fun getContent(): DefinedState<ByteArray>
    fun readAttribute(fileAttribute: FileAttribute): DefinedState<AttributeInputStream?>

    /**
     * @return [State.NotAvailable] if recovery is not possible at all, [State.Ready] if an attempt to recover children ids was made,
     * but be cautious that the result may be incomplete in any case: some children ids may get lost if log was truncated from the start.
     * Keep in mind that a file is considered a child here if its record has its parentId field set to our fileId, so it will be
     * considered a child even if the record is marked as deleted.
     * @see [notDeleted]
     */
    fun getChildrenIds(): DefinedState<RecoveredChildrenIds>

    companion object {
      val VirtualFileSnapshot.isDeleted: DefinedState<Boolean> get() =
        flags.observeState().fmap { PersistentFSRecordAccessor.hasDeletedFlag(it) }

      fun <T: VirtualFileSnapshot> Collection<T>.notDeleted(keepIfNotAvailable: Boolean = false) =
        filter { it.isDeleted.mapCases({ keepIfNotAvailable }) { !it } }
    }

    interface RecoveredChildrenIds: List<Int> {
      /**
       * `false` in case there is no evidence that the list contains all children ids (some ids may get lost, but it cannot contain ids
       * which are not actually children).
       */
      val isComplete: Boolean

      companion object {
        private class RecoveredChildrenIdsImpl(val ids: List<Int>, override val isComplete: Boolean) : RecoveredChildrenIds, List<Int> by ids

        fun of(ids: List<Int>, isComplete: Boolean): RecoveredChildrenIds = RecoveredChildrenIdsImpl(ids, isComplete)
      }
    }

    abstract class Property<T> {
      @Volatile
      var state: State = State.UnknownYet
        protected set

      protected abstract fun compute(): DefinedState<T>

      override fun toString(): String = observeState().toString()

      @Suppress("UNCHECKED_CAST")
      fun observeState(): DefinedState<T> =
        when (val s = state) {
          is DefinedState<*> -> s as DefinedState<T>
          is State.UnknownYet -> synchronized(this) {
            if (state is State.UnknownYet) {
              val result = compute()
              state = result
              return result
            }
            return state as DefinedState<T>
          }
        }

      inline fun <R> observe(onNotAvailable: (cause: NotEnoughInformationCause) -> R, onReady: (value: T) -> R): R =
        observeState().mapCases(onNotAvailable, onReady)

      fun get(): T = observe(onNotAvailable = { throw AssertionError("property expected to be Ready", it.cause) }) { it }
      fun getOrNull(): T? = observe(onNotAvailable = { null }) { it }

      companion object {
        fun <T, R> Property<T>.fmap(f: (T) -> R): Property<R> = DependentPropertyFmap(this, f)
        fun <T, R> Property<T>.bind(f: (T) -> DefinedState<R>): Property<R> = DependentPropertyBind(this, f)

        private class DependentPropertyFmap<T, R>(private val original: Property<T>,
                                                  private val transformValue: (T) -> R) : Property<R>() {
          override fun compute(): DefinedState<R> {
            return original.observeState().fmap(transformValue)
          }
        }

        private class DependentPropertyBind<T, R>(private val original: Property<T>,
                                                  private val transformValue: (T) -> DefinedState<R>) : Property<R>() {
          override fun compute(): DefinedState<R> {
            return original.observeState().bind(transformValue)
          }
        }
      }

      sealed interface State {
        object UnknownYet : State

        sealed interface DefinedState<out T> : State

        /**
         * Use [NotEnoughInformationCause] to designate a situation when there is not enough data to succeed the recovery
         * (though the process went normal). Throw [VfsRecoveryException] if an exception occurs during the recovery process and it is
         * considered not normal.
         */
        class NotAvailable(
          val cause: NotEnoughInformationCause = UnspecifiedNotAvailableException
        ) : DefinedState<Nothing> {
          override fun toString(): String = "N/A ($cause)"
        }

        class Ready<T>(val value: T) : DefinedState<T> {
          override fun toString(): String = value.toString()
        }

        companion object {
          fun <T> DefinedState<T>.get(): T = mapCases({ throw AssertionError("value expected to be available", it) }) { it }

          inline fun <T, R> DefinedState<T>.mapCases(onNotAvailable: (cause: NotEnoughInformationCause) -> R, onReady: (value: T) -> R): R = when (this) {
            is Ready<T> -> onReady(value)
            is NotAvailable -> onNotAvailable(cause)
          }

          inline fun <T, R> DefinedState<T>.fmap(f: (T) -> R): DefinedState<R> = when (this) {
            is NotAvailable -> this
            is Ready<T> -> Ready(f(value))
          }

          inline fun <T, R> DefinedState<T>.bind(f: (T) -> DefinedState<R>): DefinedState<R> = when (this) {
            is NotAvailable -> this
            is Ready<T> -> f(value)
          }

          inline fun <T> DefinedState<T>?.orIfNotAvailable(other: () -> DefinedState<T>): DefinedState<T> = when (this) {
            is Ready -> this
            is NotAvailable -> other()
            null -> other()
          }
        }
      }
    }
  }
}
