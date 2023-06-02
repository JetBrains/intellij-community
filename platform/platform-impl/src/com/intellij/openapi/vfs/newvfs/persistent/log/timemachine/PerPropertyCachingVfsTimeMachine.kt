// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

import com.intellij.openapi.vfs.newvfs.AttributeInputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSAttributeAccessor
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.constCopier
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogContext
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle.ContentRestorationSequence.Companion.restoreContent
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle.LookupResult.Companion.toState
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
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class PerPropertyCachingVfsTimeMachine(
  private val vfsLogContext: VfsLogContext,
  private val id2filename: (Int) -> String?,
  private val attributeEnumerator: SimpleStringPersistentEnumerator,
  private val payloadReader: (PayloadRef) -> DefinedState<ByteArray>
) : VfsTimeMachine {
  private val cache = sortedMapOf<Long, SoftReference<VfsSnapshot>>()
  private val zeroLayer = NotAvailableVfsSnapshot(
    vfsLogContext.operationLogStorage.begin()) // hard-reference so it won't be GCed from cache

  init {
    cache[zeroLayer.point().getPosition()] = SoftReference(zeroLayer)
  }

  override fun getSnapshot(point: OperationLogStorage.Iterator): VfsSnapshot = synchronized(this) {
    cache[point.getPosition()]?.get()?.let { return it }
    val snapshot = CacheAwarePerPropertyVfsSnapshot(point,
                                                    vfsLogContext,
                                                    id2filename,
                                                    attributeEnumerator,
                                                    payloadReader,
                                                    ::closestPrecedingCachedSnapshot)
    cache[point.getPosition()] = SoftReference(snapshot)
    return snapshot
  }

  fun closestPrecedingCachedSnapshot(point: OperationLogStorage.Iterator): VfsSnapshot? = synchronized(this) {
    val pos = point.getPosition()
    // TODO: can be optimized
    return cache.map { it.key to it.value.get() }.lastOrNull { it.first < pos && it.second != null }?.second
  }
}

class CacheAwarePerPropertyVfsSnapshot(
  point: OperationLogStorage.Iterator,
  private val logContext: VfsLogContext,
  private val id2filename: (Int) -> String?,
  private val attributeEnumerator: SimpleStringPersistentEnumerator,
  private val payloadReader: (PayloadRef) -> DefinedState<ByteArray>,
  private val getPrecedingCachedSnapshot: (point: OperationLogStorage.Iterator) -> VfsSnapshot?,
) : VfsSnapshot {
  override val point: () -> OperationLogStorage.Iterator = point.constCopier()

  private var _cachedPrecedingSnapshot: SoftReference<VfsSnapshot> = SoftReference(null)
  val precedingSnapshot: VfsSnapshot?
    get() {
      _cachedPrecedingSnapshot.get()?.let { return it }
      synchronized(this) {
        val preceding = getPrecedingCachedSnapshot(point())
        _cachedPrecedingSnapshot = SoftReference(preceding)
        return preceding
      }
    }

  private val fileCache: ConcurrentMap<Int, VirtualFileSnapshot> = ConcurrentHashMap()

  override fun getFileById(fileId: Int): VirtualFileSnapshot = fileCache.computeIfAbsent(fileId) {
    CacheAwareVirtualFileSnapshot(fileId)
  }

  inner class CacheAwareVirtualFileSnapshot(override val fileId: Int) : VirtualFileSnapshot {
    override val nameId: Property<Int> = CacheAwareProp(VirtualFileSnapshot::nameId) { stopIter, iter ->
      VfsChronicle.lookupNameId(iter, fileId, stopIf = { iter == stopIter }).toState()
    }
    override val name: Property<String> = nameId.bind {
      id2filename(it)?.let(State::Ready) ?: State.NotAvailable()
    }

    override val parentId: Property<Int> = CacheAwareProp(VirtualFileSnapshot::parentId) { stopIter, iter ->
      VfsChronicle.lookupParentId(iter, fileId, stopIf = { iter == stopIter }).toState()
    }
    override val parent: Property<VirtualFileSnapshot?> = parentId.fmap { id ->
      if (id == 0) null
      else getFileById(id)
    }

    override val length: Property<Long> = CacheAwareProp(VirtualFileSnapshot::length) { stopIter, iter ->
      VfsChronicle.lookupLength(iter, fileId, stopIf = { iter == stopIter }).toState()
    }
    override val timestamp: Property<Long> = CacheAwareProp(VirtualFileSnapshot::timestamp) { stopIter, iter ->
      VfsChronicle.lookupTimestamp(iter, fileId, stopIf = { iter == stopIter }).toState()
    }
    override val flags: Property<Int> = CacheAwareProp(VirtualFileSnapshot::flags) { stopIter, iter ->
      VfsChronicle.lookupFlags(iter, fileId, stopIf = { iter == stopIter }).toState()
    }
    override val contentRecordId: Property<Int> = CacheAwareProp(VirtualFileSnapshot::contentRecordId) { stopIter, iter ->
      VfsChronicle.lookupContentRecordId(iter, fileId, stopIf = { iter == stopIter }).toState()
    }
    override val attributesRecordId: Property<Int> = CacheAwareProp(VirtualFileSnapshot::attributesRecordId) { stopIter, iter ->
      VfsChronicle.lookupAttributeRecordId(iter, fileId, stopIf = { iter == stopIter }).toState()
    }

    override fun getContent(): DefinedState<ByteArray> =
      contentRecordId.observeState().bind { contentRecordId ->
        VfsChronicle.lookupContentRestorationStack(point(), contentRecordId).bind { it.restoreContent(payloadReader) }
      }

    override fun readAttribute(fileAttribute: FileAttribute): DefinedState<AttributeInputStream?> {
      val attrId = logContext.stringEnumerator.enumerate(fileAttribute.id)
      val attrData = VfsChronicle.lookupAttributeData(point(), fileId, attrId)
      if (!attrData.found) return State.NotAvailable()
      val payloadRef = attrData.value
      if (payloadRef == null) return State.Ready(null)
      return payloadReader(payloadRef).fmap {
        PersistentFSAttributeAccessor.validateAttributeVersion(
          fileAttribute,
          AttributeInputStream(UnsyncByteArrayInputStream(it), attributeEnumerator)
        )
      }
    }

    override fun getChildrenIds(): DefinedState<RecoveredChildrenIds> {
      return VfsChronicle.restoreChildrenIds(point(), fileId).let(State::Ready)
    }

    private inner class CacheAwareProp<T>(
      private val accessProp: VirtualFileSnapshot.() -> Property<T>,
      private val queryLog: (stopIter: OperationLogStorage.Iterator?, iter: OperationLogStorage.Iterator) -> DefinedState<T>
    ) : Property<T>() {
      override fun compute(): DefinedState<T> {
        val precedingSnapshot = precedingSnapshot
        if (precedingSnapshot != null) {
          return when (val partialLookup = queryLog(precedingSnapshot.point(), point())) {
            is State.Ready -> partialLookup
            is State.NotAvailable -> precedingSnapshot.getFileById(fileId).accessProp().observeState()
          }
        }
        return queryLog(null, point())
      }
    }
  }
}