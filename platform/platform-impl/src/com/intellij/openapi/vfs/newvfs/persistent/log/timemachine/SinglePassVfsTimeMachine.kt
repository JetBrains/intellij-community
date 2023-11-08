// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.log.*
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.constCopier
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.ExtendedVfsSnapshot.AttributeDataMap
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.ExtendedVfsSnapshot.ExtendedVirtualFileSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.FillInVfsSnapshot.SnapshotFiller
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.FillInVfsSnapshot.SnapshotFiller.Companion.fillUntilDefined
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.FillInVfsSnapshot.SnapshotFiller.Companion.finish
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.DefinedState
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle.ContentRestorationSequence
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle.ContentRestorationSequence.Companion.isFormed
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.LazyProperty
import com.intellij.util.io.SimpleStringPersistentEnumerator
import kotlinx.collections.immutable.toImmutableMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Snapshots of [SinglePassVfsTimeMachine] traverse operations log just once (and on demand) to collect all information about
 * the state of the VFS
 */
class SinglePassVfsTimeMachine(
  private val queryContext: VfsLogQueryContext,
  private val nameByNameId: (Int) -> DefinedState<String>,
  private val getAttributeEnumerator: () -> SimpleStringPersistentEnumerator,
  private val fillerSupplier: () -> SnapshotFillerPresets.Filler = { SnapshotFillerPresets.everything }
) : VfsTimeMachine {
  private val payloadReader: PayloadReader get() = queryContext.payloadReader

  override fun getSnapshot(point: OperationLogStorage.Iterator): ExtendedVfsSnapshot {
    val snapshot = FillInVfsSnapshot(point, queryContext, nameByNameId, getAttributeEnumerator, payloadReader)
    snapshot.filler = SnapshotFillerImpl(snapshot, fillerSupplier())
    return snapshot
  }

  companion object {
    class SnapshotFillerImpl(
      private val snapshot: FillInVfsSnapshot,
      private val filler: SnapshotFillerPresets.Filler,
    ) : SnapshotFiller {
      private val iter = snapshot.point()

      override fun fillUntil(condition: () -> Boolean) = synchronized(this) {
        VfsChronicle.traverseOperationsLog(
          iter, OperationLogStorage.TraverseDirection.REWIND,
          filler.relevantOperations, { condition() }
        ) { op ->
          filler.fillIn(op, snapshot)
        }
        check(condition() || !iter.hasPrevious())
      }
    }
  }
}

class FillInVfsSnapshot(point: OperationLogStorage.Iterator,
                        private val queryContext: VfsLogQueryContext,
                        private val nameByNameId: (Int) -> DefinedState<String>,
                        private val attributeEnumerator: () -> SimpleStringPersistentEnumerator,
                        override val payloadReader: PayloadReader
) : ExtendedVfsSnapshot {
  fun interface SnapshotFiller {
    /** Must not observe properties to avoid deadlock possibility -- only fillIn can be used */
    fun fillUntil(condition: () -> Boolean)

    companion object {
      fun SnapshotFiller.fillUntilDefined(property: LazyProperty<*>) = fillUntil { property.state !is State.UnknownYet }
      fun SnapshotFiller.finish() = fillUntil { false }
    }
  }

  @Volatile
  internal var filler: SnapshotFiller? = null

  override val point = point.constCopier()
  override fun enumerateAttribute(fileAttribute: FileAttribute): EnumeratedFileAttribute = queryContext.enumerateAttribute(fileAttribute)
  override fun getNameByNameId(nameId: Int): DefinedState<String> = nameByNameId(nameId)
  override fun getAttributeValueEnumerator(): SimpleStringPersistentEnumerator = attributeEnumerator()

  private val fileCache: ConcurrentMap<Int, FillInVirtualFileSnapshot> = ConcurrentHashMap()
  override fun getFileById(fileId: Int): FillInVirtualFileSnapshot = fileCache.computeIfAbsent(fileId) {
    FillInVirtualFileSnapshot(fileId, this)
  }

  override fun forEachFile(body: (ExtendedVirtualFileSnapshot) -> Unit) {
    filler?.finish() // we must know all file ids that exist in vfs before working with them
    fileCache.forEach { (_, vfile) ->
      body(vfile)
    }
  }

  private val contentRestorationSequenceMap: ConcurrentMap<Int, VfsChronicle.ContentRestorationSequenceBuilder> = ConcurrentHashMap()
  internal fun getContentRestorationSequenceBuilderFor(contentRecordId: Int) =
    contentRestorationSequenceMap.getOrPut(contentRecordId) { VfsChronicle.ContentRestorationSequenceBuilder() }

  override fun getContentRestorationSequence(contentRecordId: Int): DefinedState<ContentRestorationSequence> {
    val builder = getContentRestorationSequenceBuilderFor(contentRecordId)
    filler?.fillUntil { builder.isFormed }
    return builder.let(State::Ready)
  }

  // access must be synchronized
  class FillInVirtualFileSnapshot(override val fileId: Int, override val vfsSnapshot: FillInVfsSnapshot) : ExtendedVirtualFileSnapshot {
    override val nameId = FillInProperty<Int>()
    override val parentId = FillInProperty<Int>()
    override val length = FillInProperty<Long>()
    override val timestamp = FillInProperty<Long>()
    override val flags = FillInProperty<Int>()
    override val contentRecordId = FillInProperty<Int>()
    override val attributesRecordId = FillInProperty<Int>()

    override val attributeDataMap = FillInProperty<AttributeDataMap> {
      AttributeDataMap.of(
        formingAttributesDataMap.toImmutableMap()
          .also { formingAttributesDataMap.clear() },
        false // no DELETE ATTRS operation met
      ).let(State::Ready)
    }

    // TODO: probably this should be changed to smth like Map<Int, Pair<EnumeratedFileAttribute, PayloadRef>>, but attributeDataMap will
    //  stay the same
    /* there is a potential pitfall here: EnumeratedFileAttribute's equality check only honors ids, so if
      one would like to override an entry, they need to change not only the value, but also the key:
      e.g. if there was `formingAttributesDataMap[oldAttr] = oldRef`, then plain `formingAttributesDataMap[newAttr] = newRef`
      (given `newAttr == oldAttr`, but they may have different versions) will not change the key object and
      the map would look like `formingAttributesDataMap[oldAttr] = newRef`. This is not a problem now because
      data is put using only `putIfAbsent` method. */
    internal val formingAttributesDataMap = hashMapOf<EnumeratedFileAttribute, PayloadRef>()
    internal val attributesFinished: Boolean get() = attributeDataMap.state is State.Ready<*> // whether DELETE ATTRS operation met

    override val recordAllocationExists = FillInProperty<Boolean> { false.let(State::Ready) }

    inner class FillInProperty<T>(
      private val onNotFilled: () -> DefinedState<T> = { State.NotAvailable(UnspecifiedNotAvailableException) }
    ) : LazyProperty<T>() {
      override fun compute(): DefinedState<T> {
        vfsSnapshot.filler?.fillUntilDefined(this)
        if (state is State.UnknownYet) {
          return onNotFilled()
        }
        @Suppress("UNCHECKED_CAST")
        return state as DefinedState<T>
      }

      fun fillIn(definedState: DefinedState<T>) {
        if (state is DefinedState<*>) return
        state = definedState
      }
    }
  }
}