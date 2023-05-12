// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.vfs.newvfs.AttributeInputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl.ErrorHandler
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsOracle.LogDistanceEvaluator
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.constCopier
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogContext
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsChronicle
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsChronicle.LookupResult.Companion.toState
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.Companion.bind
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.Companion.fmap
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.mapCases
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsStateOracle
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
      override val nameId: VfsSnapshot.VirtualFileSnapshot.Property<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupNameId(point(), fileId, direction = OperationLogStorage.TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { fsRecords.getNameIdByFileId(fileId) }
      )
      override val name: VfsSnapshot.VirtualFileSnapshot.Property<String> = nameId.fmap { fsRecords.getNameByNameId(it).toString() }

      override val parentId: VfsSnapshot.VirtualFileSnapshot.Property<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupParentId(point(), fileId, direction = OperationLogStorage.TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { fsRecords.getParent(fileId) }
      )
      override val parent: VfsSnapshot.VirtualFileSnapshot.Property<VfsSnapshot.VirtualFileSnapshot?> = parentId.fmap {
        if (it == 0) null else getFileById(it)
      }

      override val length: VfsSnapshot.VirtualFileSnapshot.Property<Long> = OracledProp(
        queryLog = {
          VfsChronicle.lookupLength(point(), fileId, direction = OperationLogStorage.TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { fsRecords.getLength(fileId) }
      )
      override val timestamp: VfsSnapshot.VirtualFileSnapshot.Property<Long> = OracledProp(
        queryLog = {
          VfsChronicle.lookupTimestamp(point(), fileId, direction = OperationLogStorage.TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { fsRecords.getTimestamp(fileId) }
      )
      override val flags: VfsSnapshot.VirtualFileSnapshot.Property<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupFlags(point(), fileId, direction = OperationLogStorage.TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { fsRecords.getFlags(fileId) }
      )
      override val contentRecordId: VfsSnapshot.VirtualFileSnapshot.Property<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupContentRecordId(point(), fileId, direction = OperationLogStorage.TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { fsRecords.getContentRecordId(fileId) }
      )
      override val attributesRecordId: VfsSnapshot.VirtualFileSnapshot.Property<Int> = OracledProp(
        queryLog = {
          VfsChronicle.lookupAttributeRecordId(point(), fileId, direction = OperationLogStorage.TraverseDirection.PLAY).toState()
        },
        queryFsRecords = { fsRecords.getAttributeRecordId(fileId) }
      )

      override fun getContent(): VfsSnapshot.VirtualFileSnapshot.Property.State.DefinedState<ByteArray> = contentRecordId.bind {
        if (it == 0) return@bind VfsSnapshot.VirtualFileSnapshot.Property.State.notAvailable(
          VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.NotEnoughInformationCause("VFS didn't cache file's content"))
        if (!distanceEvaluator.isWorthLookingUpFrom(point())) return@bind VfsSnapshot.VirtualFileSnapshot.Property.State.notAvailable()
        val lookup = VfsChronicle.lookupContentOperation(point(), it, OperationLogStorage.TraverseDirection.PLAY)
        if (lookup.found) { // some operation took place in between (point(), end()) so FSRecords may not contain value as at point()
          return@bind VfsSnapshot.VirtualFileSnapshot.Property.State.notAvailable()
        }
        // content at point() is the same as in FSRecords
        return@bind fsRecords.readContentById(it).readAllBytes().let(VfsSnapshot.VirtualFileSnapshot.Property.State::ready)
      }.observeState()

      override fun readAttribute(fileAttribute: FileAttribute): VfsSnapshot.VirtualFileSnapshot.Property.State.DefinedState<AttributeInputStream?> {
        if (!distanceEvaluator.isWorthLookingUpFrom(point())) return VfsSnapshot.VirtualFileSnapshot.Property.State.notAvailable()
        val attrId = vfsLogContext.stringEnumerator.enumerate(fileAttribute.id)
        val attrData = VfsChronicle.lookupAttributeData(point(), fileId, attrId, direction = OperationLogStorage.TraverseDirection.PLAY)
        if (attrData.found) return VfsSnapshot.VirtualFileSnapshot.Property.State.notAvailable() // some operation took place in between (point(), end())
        return fsRecords.readAttributeWithLock(fileId, fileAttribute).let(VfsSnapshot.VirtualFileSnapshot.Property.State::ready)
      }

      private inner class OracledProp<T>(
        val queryLog: () -> State.DefinedState<T>,
        val queryFsRecords: () -> T,
      ) : VfsSnapshot.VirtualFileSnapshot.Property<T>() {
        override fun compute(): State.DefinedState<T> {
          if (!distanceEvaluator.isWorthLookingUpFrom(point())) return State.notAvailable()
          /* tricky: we must return the value at point(), so if queryLog() returns Ready, then the property has been changed in
             between (point(), end()) of log, so value at point() may not be the same as it is in FSRecords */
          return queryLog().mapCases(onNotAvailable = { State.ready(queryFsRecords()) }) { State.notAvailable() }
        }
      }
    }
  }
}