// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine

import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.constCopier
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.TraverseDirection
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLog
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.FSRecordsOracle.LogDistanceEvaluator
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsChronicle.LookupResult.Companion.toState
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.Companion.fmap
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.mapCases

/**
 * Symbolises an external knowledge about the state of VFS at a specified point in time
 */
typealias VfsStateOracle = (OperationLogStorage.Iterator) -> VfsSnapshot?

class FSRecordsOracle(
  private val fsRecords: FSRecordsImpl,
  private val vfsLog: VfsLog,
  private val distanceEvaluator: LogDistanceEvaluator = LogDistanceEvaluator { iterator ->
    vfsLog.query { operationLogStorage.end().getPosition() - iterator.getPosition() } < 8_000_000 // 8mb, TODO can be smarter, like 20% or smth
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

    override fun getFileByIdIfExists(fileId: Int) = getFileById(fileId)

    inner class OracledVirtualFileSnapshot(override val fileId: Int) : VfsSnapshot.VirtualFileSnapshot {
      override val nameId: Property<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupNameId(point(), fileId, direction = TraverseDirection.PLAY).toState() // TODO can throw on Invalid
        },
        queryFsRecords = { fsRecords.getNameIdByFileId(fileId) }
      )
      override val name: Property<String> = nameId.fmap {
        fsRecords.getNameByNameId(it).toString()
      }
      override val parentId: Property<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupParentId(point(), fileId, direction = TraverseDirection.PLAY).toState() // TODO can throw on Invalid
        },
        queryFsRecords = { fsRecords.getParent(fileId) }
      )
      override val parent: Property<VfsSnapshot.VirtualFileSnapshot?> = parentId.fmap {
        if (it == 0) null else OracledVirtualFileSnapshot(it)
      }

      private inner class OracledProp<T>(
        val queryLog: () -> State.DefinedState<T>,
        val queryFsRecords: () -> T,
      ) : Property<T>() {
        override fun compute(): State.DefinedState<T> {
          if (!distanceEvaluator.isWorthLookingUpFrom(point())) return State.notAvailable()
          /* tricky: we must return the value at point(), so if queryLog() returns Ready, then the property was changed in
             between (point(), end()) of log, so value at point() may not be the same as it is in FSRecords */
          return queryLog().mapCases(onNotAvailable = { State.ready(queryFsRecords()) }) { State.notAvailable() }
        }
      }
    }
  }
}