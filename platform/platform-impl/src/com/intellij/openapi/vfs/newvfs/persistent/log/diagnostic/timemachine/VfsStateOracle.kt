// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine

import com.intellij.openapi.vfs.newvfs.AttributeInputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.constCopier
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.TraverseDirection
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogContext
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.FSRecordsOracle.LogDistanceEvaluator
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsChronicle.LookupResult.Companion.toState
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.Companion.bind
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.Companion.fmap
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.mapCases
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.DefinedState

/**
 * Symbolises an external knowledge about the state of VFS at a specified point in time
 */
typealias VfsStateOracle = (OperationLogStorage.Iterator) -> VfsSnapshot?

class FSRecordsOracle(
  private val fsRecords: FSRecordsImpl,
  private val vfsLogContext: VfsLogContext,
  private val payloadReader: (PayloadRef) -> DefinedState<ByteArray>,
  private val distanceEvaluator: LogDistanceEvaluator = LogDistanceEvaluator { iterator ->
    vfsLogContext.operationLogStorage.end().getPosition() - iterator.getPosition() < 8_000_000 // 8mb, TODO can be smarter, like 20% or smth
  }
) {
  fun interface LogDistanceEvaluator {
    fun isWorthLookingUpFrom(iterator: OperationLogStorage.Iterator): Boolean
  }

  fun getSnapshot(point: OperationLogStorage.Iterator): VfsSnapshot? {
    if (!distanceEvaluator.isWorthLookingUpFrom(point)) return null
    return Snapshot(point)
  }

  private inner class Snapshot(point: OperationLogStorage.Iterator) : VfsSnapshot {
    override val point = point.constCopier()

    override fun getFileById(fileId: Int): VfsSnapshot.VirtualFileSnapshot {
      return OracledVirtualFileSnapshot(fileId)
    }

    inner class OracledVirtualFileSnapshot(override val fileId: Int) : VfsSnapshot.VirtualFileSnapshot {
      // TODO VfsChronicle.lookup* can throw on Invalid, needs to be processed correctly (propagate error as NotAvailable?)
      override val nameId: Property<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupNameId(point(), fileId, direction = TraverseDirection.PLAY).toState() // TODO can throw on Invalid
        },
        queryFsRecords = { fsRecords.getNameIdByFileId(fileId) }
      )
      override val name: Property<String> = nameId.fmap { fsRecords.getNameByNameId(it).toString() }

      override val parentId: Property<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupParentId(point(), fileId, direction = TraverseDirection.PLAY).toState() // TODO can throw on Invalid
        },
        queryFsRecords = { fsRecords.getParent(fileId) }
      )
      override val parent: Property<VfsSnapshot.VirtualFileSnapshot?> = parentId.fmap {
        if (it == 0) null else getFileById(it)
      }

      override val length: Property<Long> = OracledProp(
        queryLog = {
          VfsChronicle.lookupLength(point(), fileId, direction = TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { fsRecords.getLength(fileId) }
      )
      override val timestamp: Property<Long> = OracledProp(
        queryLog = {
          VfsChronicle.lookupTimestamp(point(), fileId, direction = TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { fsRecords.getTimestamp(fileId) }
      )
      override val flags: Property<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupFlags(point(), fileId, direction = TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { fsRecords.getFlags(fileId) }
      )
      override val contentRecordId: Property<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupContentRecordId(point(), fileId, direction = TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { fsRecords.getContentRecordId(fileId) }
      )
      override val attributesRecordId: Property<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupAttributeRecordId(point(), fileId, direction = TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { fsRecords.getAttributeRecordId(fileId) }
      )

      override fun getContent(): DefinedState<ByteArray> = contentRecordId.bind {
        val lookup = VfsChronicle.lookupContentOperation(point(), it, TraverseDirection.PLAY)
        if (lookup.found) {
          // some operation took place in between (point(), end()) so FSRecords may not contain value as at point()
          return@bind VfsChronicle.restoreContent(point(), it, payloadReader)
        }
        // content at point() is the same as in FSRecords
        return@bind fsRecords.readContentById(it).readAllBytes().let(State::ready)
      }.observeState()

      override fun readAttribute(fileAttribute: FileAttribute): DefinedState<AttributeInputStream?> {
        if (!distanceEvaluator.isWorthLookingUpFrom(point())) return State.notAvailable()
        val attrId = vfsLogContext.stringEnumerator.enumerate(fileAttribute.id)
        val attrData = VfsChronicle.lookupAttributeData(point(), fileId, attrId, direction = TraverseDirection.PLAY)
        if (attrData.found) return State.notAvailable() // some operation took place in between (point(), end())
        return fsRecords.readAttributeWithLock(fileId, fileAttribute).let(State::ready)
      }

      private inner class OracledProp<T>(
        val queryLog: () -> DefinedState<T>,
        val queryFsRecords: () -> T,
      ) : Property<T>() {
        override fun compute(): DefinedState<T> {
          if (!distanceEvaluator.isWorthLookingUpFrom(point())) return State.notAvailable()
          /* tricky: we must return the value at point(), so if queryLog() returns Ready, then the property has been changed in
             between (point(), end()) of log, so value at point() may not be the same as it is in FSRecords */
          return queryLog().mapCases(onNotAvailable = { State.ready(queryFsRecords()) }) { State.notAvailable() }
        }
      }
    }
  }
}