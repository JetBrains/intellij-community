// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

import com.intellij.openapi.vfs.newvfs.AttributeInputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.log.EnumeratedFileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadReader
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.ExtendedVfsSnapshot.AttributeDataMap.Companion.overrideWith
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.ExtendedVfsSnapshot.ExtendedVirtualFileSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.Companion.getOrDefault
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.Companion.orIfNotAvailable
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.DefinedState
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle.ContentRestorationSequence.Companion.isFormed
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle.plus
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.RecoveredChildrenIds
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property
import com.intellij.util.io.SimpleStringPersistentEnumerator
import org.jetbrains.annotations.ApiStatus

/**
 * Wraps [VfsTimeMachine] so that requests first go through [oracle], and if it can't answer the request, then the original
 * [VfsTimeMachine] processes the request.
 *
 * @see ExtendedVfsSnapshotConcat
 */
@ApiStatus.Obsolete
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
    is RecoveredChildrenIds -> {
      rhs as RecoveredChildrenIds
      var ok = true
      if (lhs.isComplete) ok = ok && lhs.containsAll(rhs)
      if (rhs.isComplete) ok = ok && rhs.containsAll(lhs)
      ok
    }
    else -> lhs == rhs
  }
}

abstract class DualSourceTimeMachine(val original: VfsTimeMachine, val oracle: VfsStateOracle) : VfsTimeMachine {
  protected abstract fun <R> alternative(originalState: () -> DefinedState<R>, oracledState: () -> DefinedState<R>?): DefinedState<R>

  override fun getSnapshot(point: OperationLogStorage.Iterator): VfsSnapshot =
    DualSourceSnapshot(original.getSnapshot(point), oracle.getSnapshot(point), this)

  private class DualSourceSnapshot(private val originalSnapshot: VfsSnapshot,
                                   private val oracleSnapshot: VfsSnapshot?,
                                   private val tm: DualSourceTimeMachine) : VfsSnapshot {
    override val point = originalSnapshot.point

    override fun getNameByNameId(nameId: Int): DefinedState<String> =
      tm.alternative({ originalSnapshot.getNameByNameId(nameId) }, { oracleSnapshot?.getNameByNameId(nameId) })

    override fun getAttributeValueEnumerator(): SimpleStringPersistentEnumerator {
      val orig = originalSnapshot.getAttributeValueEnumerator()
      oracleSnapshot?.getAttributeValueEnumerator()?.let { assert(orig == it) }
      return orig
    }

    override fun getContent(contentRecordId: Int): DefinedState<ByteArray> =
      tm.alternative({ originalSnapshot.getContent(contentRecordId) }, { oracleSnapshot?.getContent(contentRecordId) })

    override fun getFileById(fileId: Int): VirtualFileSnapshot =
      DualSourceVirtualFileSnapshot(originalSnapshot.getFileById(fileId), oracleSnapshot?.getFileById(fileId), this)

    override fun getChildrenIdsOf(fileId: Int): DefinedState<RecoveredChildrenIds> =
      tm.alternative({ originalSnapshot.getChildrenIdsOf(fileId) }, { oracleSnapshot?.getChildrenIdsOf(fileId) })

    private class DualSourceVirtualFileSnapshot(private val originalVersion: VirtualFileSnapshot,
                                                private val oracledVersion: VirtualFileSnapshot?,
                                                override val vfsSnapshot: DualSourceSnapshot) : VirtualFileSnapshot {
      init {
        assert(oracledVersion == null || originalVersion.fileId == oracledVersion.fileId)
      }

      override val fileId: Int = originalVersion.fileId

      private inline fun <R> alternativeProp(crossinline access: VirtualFileSnapshot.() -> Property<R>): Property<R> =
        object : Property<R> {
          override fun observeState(): DefinedState<R> =
            vfsSnapshot.tm.alternative({ originalVersion.access().observeState() }, { oracledVersion?.access()?.observeState() })

          override fun toString(): String = observeState().toString()
        }

      private inline fun <R> alternativeState(crossinline access: VirtualFileSnapshot.() -> DefinedState<R>): DefinedState<R> =
        vfsSnapshot.tm.alternative({ originalVersion.access() }, { oracledVersion?.access() })

      override val nameId: Property<Int> = alternativeProp { nameId }
      override val parentId: Property<Int> = alternativeProp { parentId }
      override val length: Property<Long> = alternativeProp { length }
      override val timestamp: Property<Long> = alternativeProp { timestamp }
      override val flags: Property<Int> = alternativeProp { flags }
      override val contentRecordId: Property<Int> = alternativeProp { contentRecordId }
      override val attributesRecordId: Property<Int> = alternativeProp { attributesRecordId }

      override fun getContent(): DefinedState<ByteArray> = alternativeState { getContent() }
      override fun readAttribute(fileAttribute: FileAttribute): DefinedState<AttributeInputStream?> = alternativeState {
        readAttribute(fileAttribute)
      }
    }
  }
}

/**
 * TODO
 * ```olderInstance.point().getPosition() < this.point().getPosition()```
 * `this` covers exactly the range ```(olderInstance.point().getPosition(), rhs.point().getPosition()]```
 */
fun ExtendedVfsSnapshot.precededBy(olderInstance: ExtendedVfsSnapshot): ExtendedVfsSnapshot = ExtendedVfsSnapshotConcat(olderInstance, this)

private class ExtendedVfsSnapshotConcat(
  val lhsSnapshot: ExtendedVfsSnapshot,
  val rhsSnapshot: ExtendedVfsSnapshot
) : ExtendedVfsSnapshot {
  override val payloadReader: PayloadReader = { ref ->
    rhsSnapshot.payloadReader(ref)
      .orIfNotAvailable { lhsSnapshot.payloadReader(ref) }
  }

  override fun enumerateAttribute(fileAttribute: FileAttribute): EnumeratedFileAttribute =
    rhsSnapshot.enumerateAttribute(fileAttribute)

  override fun getFileById(fileId: Int): ExtendedVirtualFileSnapshot =
    ExtendedVirtualFileSnapshotSum(lhsSnapshot.getFileById(fileId), rhsSnapshot.getFileById(fileId), this)

  override fun forEachFile(body: (ExtendedVirtualFileSnapshot) -> Unit) {
    val allFileIds = sortedSetOf<Int>()
    lhsSnapshot.forEachFile { allFileIds.add(it.fileId) }
    rhsSnapshot.forEachFile { allFileIds.add(it.fileId) }
    allFileIds.forEach {
      body(getFileById(it))
    }
  }

  override fun getContentRestorationSequence(contentRecordId: Int): DefinedState<VfsChronicle.ContentRestorationSequence> {
    val right = rhsSnapshot.getContentRestorationSequence(contentRecordId)
    if (right is State.Ready && right.value.isFormed) return right
    val left = lhsSnapshot.getContentRestorationSequence(contentRecordId)
    if (left is State.NotAvailable && right is State.NotAvailable) return left
    return (left.getOrDefault { VfsChronicle.ContentRestorationSequenceBuilder() } +
            right.getOrDefault { VfsChronicle.ContentRestorationSequenceBuilder() }).let(State::Ready)
  }

  override val point: () -> OperationLogStorage.Iterator get() = rhsSnapshot.point

  override fun getNameByNameId(nameId: Int): DefinedState<String> =
    rhsSnapshot.getNameByNameId(nameId).orIfNotAvailable { lhsSnapshot.getNameByNameId(nameId) }

  override fun getAttributeValueEnumerator(): SimpleStringPersistentEnumerator {
    val rhsEnum = rhsSnapshot.getAttributeValueEnumerator()
    assert(rhsEnum == lhsSnapshot.getAttributeValueEnumerator())
    return rhsEnum
  }

  class ExtendedVirtualFileSnapshotSum(
    val lhs: ExtendedVirtualFileSnapshot,
    val rhs: ExtendedVirtualFileSnapshot,
    override val vfsSnapshot: ExtendedVfsSnapshot,
  ) : ExtendedVirtualFileSnapshot {
    private inline fun <T> overwriteProp(crossinline prop: ExtendedVirtualFileSnapshot.() -> Property<T>): Property<T> =
      object : Property<T> {
        override fun observeState(): DefinedState<T> =
          rhs.prop().observeState().orIfNotAvailable { lhs.prop().observeState() }

        override fun toString(): String = observeState().toString()
      }

    private inline fun <T> mergeProp(crossinline prop: ExtendedVirtualFileSnapshot.() -> Property<T>,
                                     crossinline merge: (left: T, right: T) -> T): Property<T> =
      object : Property<T> {
        override fun observeState(): DefinedState<T> {
          val right = rhs.prop().observeState()
          if (right is State.NotAvailable) return lhs.prop().observeState()
          right as State.Ready
          val left = lhs.prop().observeState()
          if (left is State.NotAvailable) return right
          left as State.Ready
          return merge(left.value, right.value).let(State::Ready)
        }

        override fun toString(): String = observeState().toString()
      }

    override val fileId: Int get() = lhs.fileId
    override val nameId: Property<Int> = overwriteProp { nameId }
    override val parentId: Property<Int> = overwriteProp { parentId }
    override val length: Property<Long> = overwriteProp { length }
    override val timestamp: Property<Long> = overwriteProp { timestamp }
    override val flags: Property<Int> = overwriteProp { flags }
    override val contentRecordId: Property<Int> = overwriteProp { contentRecordId }
    override val attributesRecordId: Property<Int> = overwriteProp { attributesRecordId }

    override val recordAllocationExists: Property<Boolean> =
      mergeProp({ recordAllocationExists }) { lhs, rhs -> lhs || rhs }

    override val attributeDataMap: Property<ExtendedVfsSnapshot.AttributeDataMap> =
      mergeProp({ attributeDataMap }, { lhs, rhs -> lhs.overrideWith(rhs) })
  }
}