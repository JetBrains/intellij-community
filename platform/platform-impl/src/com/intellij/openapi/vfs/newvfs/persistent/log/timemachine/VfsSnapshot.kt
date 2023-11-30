// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

import com.intellij.openapi.vfs.newvfs.AttributeInputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordAccessor
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.Companion.bind
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.Companion.fmap
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.Companion.get
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.Companion.getOrNull
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.Companion.mapCases
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.DefinedState
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Companion.notDeleted
import com.intellij.util.io.SimpleStringPersistentEnumerator

interface VfsSnapshot {
  val point: () -> OperationLogStorage.Iterator

  fun getNameByNameId(nameId: Int): DefinedState<String>
  fun getAttributeValueEnumerator(): SimpleStringPersistentEnumerator
  fun getContent(contentRecordId: Int): DefinedState<ByteArray>

  fun getFileById(fileId: Int): VirtualFileSnapshot

  /**
   * @return [State.NotAvailable] if recovery is not possible at all, [State.Ready] if an attempt to recover children ids was made,
   * but be cautious that the result may be incomplete in any case: some children ids may get lost if log was truncated from the start.
   * Keep in mind that a file is considered a child here if its record has its parentId field set to our fileId, so it will be
   * considered a child even if the record is marked as deleted.
   * @see [notDeleted]
   */
  fun getChildrenIdsOf(fileId: Int): DefinedState<RecoveredChildrenIds>

  interface VirtualFileSnapshot {
    val vfsSnapshot: VfsSnapshot
    val fileId: Int

    val nameId: Property<Int>
    val parentId: Property<Int>
    val length: Property<Long>
    val timestamp: Property<Long>
    val flags: Property<@PersistentFS.Attributes Int>
    val contentRecordId: Property<Int>
    /**
     * Use this property only if you know for sure you need it. Consider using [readAttribute] instead.
     * @see com.intellij.openapi.vfs.newvfs.persistent.VfsRecoveryUtils.recoverFromPoint
     */
    val attributesRecordId: Property<Int>

    fun getName(): DefinedState<String> = nameId.observeState().bind {
      vfsSnapshot.getNameByNameId(it)
    }

    fun getParent(): DefinedState<VirtualFileSnapshot?> = parentId.observeState().fmap {
      if (it == 0) null else vfsSnapshot.getFileById(it)
    }

    fun getContent(): DefinedState<ByteArray> = contentRecordId.observeState().bind {
      if (it == 0) return@bind State.notEnoughInformation("VFS didn't cache content of file $fileId")
      vfsSnapshot.getContent(it)
    }

    fun readAttribute(fileAttribute: FileAttribute): DefinedState<AttributeInputStream?>

    companion object {
      val VirtualFileSnapshot.isDeleted: DefinedState<Boolean> get() =
        flags.observeState().fmap { PersistentFSRecordAccessor.hasDeletedFlag(it) }

      fun <T: VirtualFileSnapshot> Collection<T>.notDeleted(keepIfNotAvailable: Boolean = false) =
        filter { it.isDeleted.mapCases({ keepIfNotAvailable }) { !it } }
    }

    interface Property<out T> {
      fun observeState(): DefinedState<T>

      companion object {
        fun <T> Property<T>.get(): T = observeState().get()
        fun <T> Property<T>.getOrNull(): T? = observeState().getOrNull()
      }
    }

    abstract class LazyProperty<out T>: Property<T> {
      @Volatile
      var state: State = State.UnknownYet
        protected set

      protected abstract fun compute(): DefinedState<T>

      override fun toString(): String = observeState().toString()

      @Suppress("UNCHECKED_CAST")
      override fun observeState(): DefinedState<T> =
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

      inline fun <R> observe(onNotAvailable: (cause: NotAvailableException) -> R, onReady: (value: T) -> R): R =
        observeState().mapCases(onNotAvailable, onReady)
    }
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
}
