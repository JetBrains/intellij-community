// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.vfs.newvfs.AttributeInputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl.ErrorHandler
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsOracle.LogDistanceEvaluator
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.constCopier
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.TraverseDirection
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogQueryContext
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.Companion.mapCases
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle.LookupResult.Companion.toState
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.LazyProperty
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsStateOracle
import com.intellij.util.io.SimpleStringPersistentEnumerator
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * May be used only in ReadAction and there must be no pending writes in VfsLog.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
class FSRecordsOracle(
  cacheDir: Path,
  errorHandler: ErrorHandler,
  private val queryContext: VfsLogQueryContext,
  private val distanceEvaluator: LogDistanceEvaluator = LogDistanceEvaluator { iterator ->
    queryContext.end().getPosition() - iterator.getPosition() < 8_000_000 // 8mb, TODO can be smarter, like 20% or smth
  }
) : VfsStateOracle {
  private val fsRecords: FSRecordsImpl = FSRecordsImpl.connect(cacheDir, emptyList(), false, errorHandler)

  fun interface LogDistanceEvaluator {
    fun isWorthLookingUpFrom(iterator: OperationLogStorage.Iterator): Boolean
  }

  fun getNameByNameId(nameId: Int): State.DefinedState<String> {
    return fsRecords.getNameByNameId(nameId)?.toString()?.let(State::Ready) ?: State.NotAvailable()
  }

  fun disposeConnection(): Unit = fsRecords.close()

  override fun getSnapshot(point: OperationLogStorage.Iterator): VfsSnapshot? {
    if (!distanceEvaluator.isWorthLookingUpFrom(point)) return null
    return Snapshot(point, this)
  }

  private class Snapshot(point: OperationLogStorage.Iterator, val oracle: FSRecordsOracle) : VfsSnapshot {
    override val point = point.constCopier()
    override fun getNameByNameId(nameId: Int): State.DefinedState<String> {
      return oracle.getNameByNameId(nameId)
    }

    override fun getAttributeValueEnumerator(): SimpleStringPersistentEnumerator = oracle.fsRecords.enumeratedAttributes

    override fun getContent(contentRecordId: Int): State.DefinedState<ByteArray> {
      if (!oracle.distanceEvaluator.isWorthLookingUpFrom(point())) return State.NotAvailable()
      val lookup = VfsChronicle.lookupContentOperation(point(), contentRecordId, TraverseDirection.PLAY)
      if (lookup.found) { // some operation took place in between (point(), end()) so FSRecords may not contain value as at point()
        return State.NotAvailable()
      }
      // content at point() is the same as in FSRecords
      return oracle.fsRecords.readContentById(contentRecordId).readAllBytes().let(State::Ready)
    }

    override fun getFileById(fileId: Int): VfsSnapshot.VirtualFileSnapshot {
      return OracledVirtualFileSnapshot(fileId, this)
    }

    override fun getChildrenIdsOf(fileId: Int): State.DefinedState<VfsSnapshot.RecoveredChildrenIds> {
      if (point() == oracle.queryContext.end()) {
        val childrenIds = oracle.fsRecords.listIds(fileId).toList()
        return VfsSnapshot.RecoveredChildrenIds.of(childrenIds, true).let(State::Ready)
      }
      // it's not clear how to easily check that events in (point(), end()) don't mutate children ids of fileId
      return State.NotAvailable()
    }

    class OracledVirtualFileSnapshot(override val fileId: Int, override val vfsSnapshot: Snapshot) : VfsSnapshot.VirtualFileSnapshot {
      override val nameId: LazyProperty<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupNameId(vfsSnapshot.point(), fileId, direction = TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { vfsSnapshot.oracle.fsRecords.getNameIdByFileId(fileId) }
      )

      override val parentId: LazyProperty<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupParentId(vfsSnapshot.point(), fileId, direction = TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { vfsSnapshot.oracle.fsRecords.getParent(fileId) }
      )

      override val length: LazyProperty<Long> = OracledProp(
        queryLog = {
          VfsChronicle.lookupLength(vfsSnapshot.point(), fileId, direction = TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { vfsSnapshot.oracle.fsRecords.getLength(fileId) }
      )
      override val timestamp: LazyProperty<Long> = OracledProp(
        queryLog = {
          VfsChronicle.lookupTimestamp(vfsSnapshot.point(), fileId, direction = TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { vfsSnapshot.oracle.fsRecords.getTimestamp(fileId) }
      )
      override val flags: LazyProperty<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupFlags(vfsSnapshot.point(), fileId, direction = TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { vfsSnapshot.oracle.fsRecords.getFlags(fileId) }
      )
      override val contentRecordId: LazyProperty<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupContentRecordId(vfsSnapshot.point(), fileId, direction = TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { vfsSnapshot.oracle.fsRecords.getContentRecordId(fileId) }
      )
      override val attributesRecordId: LazyProperty<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupAttributeRecordId(vfsSnapshot.point(), fileId, direction = TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { vfsSnapshot.oracle.fsRecords.getAttributeRecordId(fileId) }
      )

      override fun readAttribute(fileAttribute: FileAttribute): State.DefinedState<AttributeInputStream?> {
        if (!vfsSnapshot.oracle.distanceEvaluator.isWorthLookingUpFrom(vfsSnapshot.point())) return State.NotAvailable()
        val attrId = vfsSnapshot.oracle.queryContext.enumerateAttribute(fileAttribute)
        val attrData = VfsChronicle.lookupAttributeData(vfsSnapshot.point(), fileId, attrId, direction = TraverseDirection.PLAY)
        if (attrData.found) return State.NotAvailable() // some operation took place in between (point(), end())
        return vfsSnapshot.oracle.fsRecords.readAttributeWithLock(fileId, fileAttribute).let(State::Ready)
      }

      private inner class OracledProp<T>(
        val queryLog: () -> State.DefinedState<T>,
        val queryFsRecords: () -> T,
      ) : LazyProperty<T>() {
        override fun compute(): State.DefinedState<T> {
          if (!vfsSnapshot.oracle.distanceEvaluator.isWorthLookingUpFrom(vfsSnapshot.point())) return State.NotAvailable()
          /* tricky: we must return the value at point(), so if queryLog() returns Ready, then the property has been changed in
             between (point(), end()) of log, so value at point() may not be the same as it is in FSRecords */
          return queryLog().mapCases(onNotAvailable = { State.Ready(queryFsRecords()) }) { State.NotAvailable() }
        }
      }
    }
  }
}