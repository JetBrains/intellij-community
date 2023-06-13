// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

import com.intellij.openapi.vfs.newvfs.AttributeInputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSAttributeAccessor
import com.intellij.openapi.vfs.newvfs.persistent.log.*
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.constCopier
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.AttributesOperation.Companion.fileId
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.ContentsOperation.Companion.contentRecordId
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.RecordsOperation.Companion.fileId
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperationTagsMask.Companion.union
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.ExtendedVfsSnapshot.ExtendedVirtualFileSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.FillInVfsSnapshot.FillInVirtualFileSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.FillInVfsSnapshot.FillInVirtualFileSnapshot.FillInProperty
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.FillInVfsSnapshot.SnapshotFiller
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.FillInVfsSnapshot.SnapshotFiller.Companion.fillUntilDefined
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.FillInVfsSnapshot.SnapshotFiller.Companion.finish
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle.ContentRestorationSequence
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle.ContentRestorationSequence.Companion.restoreContent
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsModificationContract.ContentOperation
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsModificationContract.PropertyOverwriteRule
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsModificationContract.isRelevantAndModifies
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.Companion.bind
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.Companion.fmap
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.bind
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.fmap
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.DefinedState
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.RecoveredChildrenIds
import com.intellij.util.io.SimpleStringPersistentEnumerator
import com.intellij.util.io.UnsyncByteArrayInputStream
import kotlinx.collections.immutable.toImmutableMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Snapshots of [SinglePassVfsTimeMachine] traverse operations log just once (and on demand) to collect all information about
 * the state of the VFS
 */
class SinglePassVfsTimeMachine(
  private val logContext: VfsLogContext,
  private val id2filename: (Int) -> String?,
  private val attributeEnumerator: SimpleStringPersistentEnumerator,
  private val payloadReader: (PayloadRef) -> DefinedState<ByteArray>
) : VfsTimeMachine {
  override fun getSnapshot(point: OperationLogStorage.Iterator): ExtendedVfsSnapshot {
    val snapshot = FillInVfsSnapshot(point, logContext, id2filename, attributeEnumerator, payloadReader)
    val filler = EverythingSnapshotFiller(snapshot)
    snapshot.filler = filler
    return snapshot
  }

  companion object {
    class EverythingSnapshotFiller(private val snapshot: FillInVfsSnapshot) : SnapshotFiller {
      private val iter = snapshot.point()

      override fun fillUntil(condition: () -> Boolean) = synchronized(this) {
        VfsChronicle.traverseOperationsLog(
          iter, OperationLogStorage.TraverseDirection.REWIND,
          toReadMask, { condition() }
        ) { op ->
          if (op is VfsOperation.RecordsOperation<*>) {
            for (relation in recordsOperationFill) {
              relation.fillInIfModifies(op) { snapshot.getFileById(op.fileId!!) }
            }
          }
          if (op is VfsOperation.AttributesOperation<*>) {
            val ifRelevantAndModifies = VfsModificationContract.attributeData.isRelevantAndModifies
            op.ifRelevantAndModifies {
              val file = snapshot.getFileById(op.fileId!!)
              if (it.enumeratedAttributeFilter == null) {
                assert(it.data == null) // deletion
                if (!file.attributesFinished) {
                  file.attributeDataMap.fillIn(file.formingAttributesDataMap.toImmutableMap().let(State::Ready))
                  file.formingAttributesDataMap.clear()
                  file.attributesFinished = true
                }
              }
              else if (!file.attributesFinished) {
                file.formingAttributesDataMap.putIfAbsent(it.enumeratedAttributeFilter, it.data!!) // write must have data
              }
            }
          }
          if (op is VfsOperation.ContentsOperation<*>) {
            val ifRelevantAndModifies = VfsModificationContract.content.isRelevantAndModifies
            op.ifRelevantAndModifies {
              val restorationSeq = snapshot.getContentRestorationSequenceBuilderFor(op.contentRecordId!!)
              if (!restorationSeq.isFormed) {
                when (it) {
                  is ContentOperation.Modify -> restorationSeq.prependModification(it)
                  is ContentOperation.Set -> restorationSeq.setInitial(it)
                }
              }
            }
          }
        }
      }

      companion object {
        private class RulePropertyRelation<T>(val modificationRule: PropertyOverwriteRule<T>,
                                              val property: FillInVirtualFileSnapshot.() -> FillInProperty<T>) {
          fun fillInIfModifies(operation: VfsOperation<*>, getFile: () -> FillInVirtualFileSnapshot) {
            val ifRelevantAndModifies = modificationRule.isRelevantAndModifies
            operation.ifRelevantAndModifies {
              getFile().property().fillIn(it.let(State::Ready))
            }
          }
        }

        private val recordsOperationFill = listOf(
          RulePropertyRelation(VfsModificationContract.nameId, FillInVirtualFileSnapshot::nameId),
          RulePropertyRelation(VfsModificationContract.parentId, FillInVirtualFileSnapshot::parentId),
          RulePropertyRelation(VfsModificationContract.length, FillInVirtualFileSnapshot::length),
          RulePropertyRelation(VfsModificationContract.timestamp, FillInVirtualFileSnapshot::timestamp),
          RulePropertyRelation(VfsModificationContract.flags, FillInVirtualFileSnapshot::flags),
          RulePropertyRelation(VfsModificationContract.contentRecordId, FillInVirtualFileSnapshot::contentRecordId),
          RulePropertyRelation(VfsModificationContract.attributeRecordId, FillInVirtualFileSnapshot::attributesRecordId),
          RulePropertyRelation(
            PropertyOverwriteRule(VfsOperationTagsMask(VfsOperationTag.REC_ALLOC)) { setValue ->
              if (this !is VfsOperation.RecordsOperation.AllocateRecord)
                throw AssertionError("operation $this does not allocate record")
              if (result.hasValue) setValue(true)
            },
            FillInVirtualFileSnapshot::recordAllocationExists
          )
        )

        private val toReadMask =
          (recordsOperationFill.map { it.modificationRule.relevantOperations } +
           listOf(VfsModificationContract.attributeData.relevantOperations,
                  VfsModificationContract.content.relevantOperations)
          ).union()
      }
    }
  }
}

class FillInVfsSnapshot(point: OperationLogStorage.Iterator,
                        private val logContext: VfsLogContext,
                        private val id2filename: (Int) -> String?,
                        private val attributeEnumerator: SimpleStringPersistentEnumerator,
                        private val payloadReader: (PayloadRef) -> DefinedState<ByteArray>
) : ExtendedVfsSnapshot {
  fun interface SnapshotFiller {
    /** Must not observe properties to avoid deadlock possibility -- only fillIn can be used */
    fun fillUntil(condition: () -> Boolean)

    companion object {
      fun SnapshotFiller.fillUntilDefined(property: Property<*>) = fillUntil { property.state !is State.UnknownYet }
      fun SnapshotFiller.finish() = fillUntil { false }
    }
  }

  @Volatile
  internal var filler: SnapshotFiller? = null

  override val point = point.constCopier()
  private val fileCache: ConcurrentMap<Int, FillInVirtualFileSnapshot> = ConcurrentHashMap()

  override fun getFileById(fileId: Int): FillInVirtualFileSnapshot = fileCache.computeIfAbsent(fileId) {
    FillInVirtualFileSnapshot(fileId)
  }

  private val contentRestorationSequenceMap: ConcurrentMap<Int, VfsChronicle.ContentRestorationSequenceBuilder> = ConcurrentHashMap()

  internal fun getContentRestorationSequenceBuilderFor(contentRecordId: Int) =
    contentRestorationSequenceMap.getOrPut(contentRecordId) { VfsChronicle.ContentRestorationSequenceBuilder() }

  fun forEachFile(body: (FillInVirtualFileSnapshot) -> Unit) {
    filler?.finish() // we must know all file ids that exist in vfs before working with them
    fileCache.forEach { (_, vfile) ->
      body(vfile)
    }
  }

  // access must be synchronized
  inner class FillInVirtualFileSnapshot(override val fileId: Int) : ExtendedVirtualFileSnapshot {
    override val nameId = FillInProperty<Int>()
    override val parentId = FillInProperty<Int>()
    override val length = FillInProperty<Long>()
    override val timestamp = FillInProperty<Long>()
    override val flags = FillInProperty<Int>()
    override val contentRecordId = FillInProperty<Int>()
    override val attributesRecordId = FillInProperty<Int>()

    override val name: Property<String> = nameId.bind {
      id2filename(it)?.let(State::Ready) ?: State.NotAvailable()
    }
    override val parent: Property<VirtualFileSnapshot?> = parentId.fmap {
      if (it == 0) null else getFileById(it)
    }

    override val attributeDataMap = FillInProperty<Map<EnumeratedFileAttribute, PayloadRef>> {
      formingAttributesDataMap.toImmutableMap().let(State::Ready) // there may be no DELETE ATTRS operation
        .also { formingAttributesDataMap.clear() }
    }

    internal var attributesFinished = false // DELETE ATTRS operation met
    internal val formingAttributesDataMap = mutableMapOf<EnumeratedFileAttribute, PayloadRef>()

    override val recordAllocationExists = FillInProperty<Boolean> { false.let(State::Ready) }

    override val contentRestorationSequence: Property<ContentRestorationSequence> = contentRecordId.bind {
      if (it == 0) return@bind State.NotAvailable(NotEnoughInformationCause("VFS didn't cache file's content"))
      val restorationSeq = getContentRestorationSequenceBuilderFor(it)
      filler?.fillUntil { restorationSeq.isFormed }
      restorationSeq.buildIfInitialIsPresent()?.let(State::Ready) // TODO clear builder to not waste memory
        ?: State.NotAvailable(NotEnoughInformationCause("Initial content wasn't found for contentRecordId $it"))
    }

    override fun getContent(): DefinedState<ByteArray> =
      contentRestorationSequence.observeState().bind { it.restoreContent(payloadReader) }

    override fun readAttribute(fileAttribute: FileAttribute): DefinedState<AttributeInputStream?> {
      val attrId = logContext.enumerateAttribute(fileAttribute)
      val attrDataRef = attributeDataMap.getOrNull()?.get(attrId) ?: return State.NotAvailable()
      return payloadReader(attrDataRef).fmap {
        PersistentFSAttributeAccessor.validateAttributeVersion(
          fileAttribute,
          AttributeInputStream(UnsyncByteArrayInputStream(it), attributeEnumerator)
        )
      }
    }

    override fun getChildrenIds(): DefinedState<RecoveredChildrenIds> {
      val childrenIds = mutableListOf<Int>()
      forEachFile {
        if (it.parentId.getOrNull() == fileId) {
          childrenIds.add(it.fileId)
        }
      }
      return RecoveredChildrenIds.of(childrenIds, recordAllocationExists.get()).let(State::Ready)
    }

    inner class FillInProperty<T>(
      private val onNotFilled: () -> DefinedState<T> = { State.NotAvailable(UnspecifiedNotAvailableException) }
    ) : Property<T>() {
      override fun compute(): DefinedState<T> {
        filler?.fillUntilDefined(this)
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