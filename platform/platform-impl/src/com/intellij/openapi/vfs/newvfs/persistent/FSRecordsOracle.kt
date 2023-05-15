// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.vfs.newvfs.AttributeInputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl.ErrorHandler
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsOracle.LogDistanceEvaluator
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.constCopier
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.TraverseDirection
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogContext
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle.LookupResult.Companion.toState
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.Companion.bind
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.Companion.fmap
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.mapCases
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.RecoveredChildrenIds
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsStateOracle
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
@ApiStatus.Experimental
class FSRecordsOracle(
  cacheDir: Path,
  errorHandler: ErrorHandler,
  private val vfsLogContext: VfsLogContext,
  private val distanceEvaluator: LogDistanceEvaluator = LogDistanceEvaluator { iterator ->
    vfsLogContext.operationLogStorage.end().getPosition() - iterator.getPosition() < 8_000_000 // 8mb, TODO can be smarter, like 20% or smth
  }
) : VfsStateOracle {
  val fsRecords = FSRecordsImpl.connect(cacheDir, emptyList(), errorHandler)

  fun interface LogDistanceEvaluator {
    fun isWorthLookingUpFrom(iterator: OperationLogStorage.Iterator): Boolean
  }

  fun getNameByNameId(id: Int): String? = fsRecords.getNameByNameId(id)?.toString()

  fun disposeConnection() = fsRecords.dispose()

  override fun getSnapshot(point: OperationLogStorage.Iterator): VfsSnapshot? {
    if (!distanceEvaluator.isWorthLookingUpFrom(point)) return null
    return Snapshot(point)
  }

  private inner class Snapshot(point: OperationLogStorage.Iterator) : VfsSnapshot {
    override val point = point.constCopier()

    override fun getFileById(fileId: Int): VfsSnapshot.VirtualFileSnapshot {
      return OracledVirtualFileSnapshot(fileId)
    }

    inner class OracledVirtualFileSnapshot(override val fileId: Int) : VfsSnapshot.VirtualFileSnapshot {
      override val nameId: Property<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupNameId(point(), fileId, direction = TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { fsRecords.getNameIdByFileId(fileId) }
      )
      override val name: Property<String> = nameId.bind {
        fsRecords.getNameByNameId(it)?.toString()?.let(State::Ready) ?: State.NotAvailable()
      }

      override val parentId: Property<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupParentId(point(), fileId, direction = TraverseDirection.PLAY).toState()
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

      override fun getContent(): State.DefinedState<ByteArray> = contentRecordId.bind {
        if (it == 0) return@bind State.NotAvailable()
        if (!distanceEvaluator.isWorthLookingUpFrom(point())) return@bind State.NotAvailable()
        val lookup = VfsChronicle.lookupContentOperation(point(), it, TraverseDirection.PLAY)
        if (lookup.found) { // some operation took place in between (point(), end()) so FSRecords may not contain value as at point()
          return@bind State.NotAvailable()
        }
        // content at point() is the same as in FSRecords
        return@bind fsRecords.readContentById(it).readAllBytes().let(State::Ready)
      }.observeState()

      override fun readAttribute(fileAttribute: FileAttribute): State.DefinedState<AttributeInputStream?> {
        if (!distanceEvaluator.isWorthLookingUpFrom(point())) return State.NotAvailable()
        val attrId = vfsLogContext.stringEnumerator.enumerate(fileAttribute.id)
        val attrData = VfsChronicle.lookupAttributeData(point(), fileId, attrId, direction = TraverseDirection.PLAY)
        if (attrData.found) return State.NotAvailable() // some operation took place in between (point(), end())
        return fsRecords.readAttributeWithLock(fileId, fileAttribute).let(State::Ready)
      }

      override fun getRecoverableChildrenIds(): State.DefinedState<RecoveredChildrenIds> {
        if (point() == vfsLogContext.operationLogStorage.end()) {
          val childrenIds = fsRecords.listIds(fileId).toList()
          return object : RecoveredChildrenIds, List<Int> by childrenIds {
            override val isComplete: Boolean = true
          }.let(State::Ready)
        }
        // it's not clear how to easily check that events in (point(), end()) don't mutate children ids of fileId
        return State.NotAvailable()
      }

      private inner class OracledProp<T>(
        val queryLog: () -> State.DefinedState<T>,
        val queryFsRecords: () -> T,
      ) : Property<T>() {
        override fun compute(): State.DefinedState<T> {
          if (!distanceEvaluator.isWorthLookingUpFrom(point())) return State.NotAvailable()
          /* tricky: we must return the value at point(), so if queryLog() returns Ready, then the property has been changed in
             between (point(), end()) of log, so value at point() may not be the same as it is in FSRecords */
          return queryLog().mapCases(onNotAvailable = { State.Ready(queryFsRecords()) }) { State.NotAvailable() }
        }
      }
    }
  }
}