// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine

import com.intellij.openapi.vfs.newvfs.AttributeInputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.orIfNotAvailable
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.DefinedState

/**
 * Wraps [VfsTimeMachine] so that requests first go through [oracle], and if it can't answer the request, then the original
 * [VfsTimeMachine] processes the request.
 */
fun VfsTimeMachine.withOracle(oracle: VfsStateOracle): VfsTimeMachine = object : DualSourceTimeMachine(this, oracle) {
  override fun <R> alternative(originalState: () -> DefinedState<R>, oracledState: () -> DefinedState<R>?): DefinedState<R> =
    oracledState().orIfNotAvailable { originalState() }
}

/**
 * Like [withOracle], but check that [VfsStateOracle] and [VfsTimeMachine] doesn't contradict each other is enforced
 * @param deepEquals may modify the passed objects
 */
fun VfsTimeMachine.withContradictionCheck(
  oracle: VfsStateOracle,
  deepEquals: (lhs: Any, rhs: Any) -> Boolean = defaultDeepEquals
): VfsTimeMachine = object : DualSourceTimeMachine(
  this, oracle) {
  override fun <R> alternative(originalState: () -> DefinedState<R>, oracledState: () -> DefinedState<R>?): DefinedState<R> {
    val original = originalState()
    val oracled = oracledState() ?: return original
    if (oracled is State.Ready && original is State.Ready) {
      if (original.value != null && oracled.value != null) {
        check(deepEquals(original.value, oracled.value)) { "contradiction detected" }
        return oracledState()!! // deepEquals may have modified the values
      }
      else {
        check((original.value == null) == (oracled.value == null)) { "contradiction detected" }
      }
    }
    return original.orIfNotAvailable { oracled }
  }
}

private val defaultDeepEquals = { lhs: Any, rhs: Any ->
  when (lhs) {
    is ByteArray -> lhs.contentEquals(rhs as ByteArray)
    is VirtualFileSnapshot -> lhs.fileId == (rhs as VirtualFileSnapshot).fileId
    is AttributeInputStream -> {
      rhs as AttributeInputStream
      val lhsBytes = lhs.readAllBytes()
      val rhsBytes = rhs.readAllBytes()
      lhsBytes.contentEquals(rhsBytes)
    }
    is VirtualFileSnapshot.RecoveredChildrenIds -> {
      rhs as VirtualFileSnapshot.RecoveredChildrenIds
      var ok = true
      if (lhs.isComplete) ok = ok and lhs.containsAll(rhs)
      if (rhs.isComplete) ok = ok and rhs.containsAll(lhs)
      ok
    }
    else -> lhs == rhs
  }
}

abstract class DualSourceTimeMachine(val original: VfsTimeMachine, val oracle: VfsStateOracle) : VfsTimeMachine {
  protected abstract fun <R> alternative(originalState: () -> DefinedState<R>, oracledState: () -> DefinedState<R>?): DefinedState<R>

  override fun getSnapshot(point: OperationLogStorage.Iterator): VfsSnapshot =
    DualSourceSnapshot(original.getSnapshot(point), oracle.getSnapshot(point))

  private inner class DualSourceSnapshot(private val originalSnapshot: VfsSnapshot,
                                         private val oracleSnapshot: VfsSnapshot?) : VfsSnapshot {
    override val point = originalSnapshot.point

    override fun getFileById(fileId: Int): VirtualFileSnapshot =
      DualSourceVirtualFileSnapshot(originalSnapshot.getFileById(fileId), oracleSnapshot?.getFileById(fileId))

    private inner class DualSourceVirtualFileSnapshot(private val originalVersion: VirtualFileSnapshot,
                                                      private val oracledVersion: VirtualFileSnapshot?) : VirtualFileSnapshot {
      init {
        assert(oracledVersion == null || originalVersion.fileId == oracledVersion.fileId)
      }

      override val fileId: Int = originalVersion.fileId

      private inline fun <R> alternativeProp(crossinline access: VirtualFileSnapshot.() -> Property<R>): Property<R> =
        object : Property<R>() {
          override fun compute(): DefinedState<R> =
            alternative({ originalVersion.access().observeState() }, { oracledVersion?.access()?.observeState() })
        }

      private inline fun <R> alternativeState(crossinline access: VirtualFileSnapshot.() -> DefinedState<R>): DefinedState<R> =
        alternative({ originalVersion.access() }, { oracledVersion?.access() })

      override val nameId: Property<Int> = alternativeProp { nameId }
      override val parentId: Property<Int> = alternativeProp { parentId }
      override val length: Property<Long> = alternativeProp { length }
      override val timestamp: Property<Long> = alternativeProp { timestamp }
      override val flags: Property<Int> = alternativeProp { flags }
      override val contentRecordId: Property<Int> = alternativeProp { contentRecordId }
      override val attributesRecordId: Property<Int> = alternativeProp { attributesRecordId }
      override val name: Property<String> = alternativeProp { name }
      override val parent: Property<VirtualFileSnapshot?> = alternativeProp { parent }

      override fun getContent(): DefinedState<ByteArray> = alternativeState { getContent() }
      override fun readAttribute(fileAttribute: FileAttribute): DefinedState<AttributeInputStream?> = alternativeState {
        readAttribute(fileAttribute)
      }
      override fun getRecoverableChildrenIds(): DefinedState<VirtualFileSnapshot.RecoveredChildrenIds> = alternativeState {
        getRecoverableChildrenIds()
      }
    }
  }
}