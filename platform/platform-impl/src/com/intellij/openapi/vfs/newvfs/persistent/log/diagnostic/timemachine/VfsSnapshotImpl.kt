// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine

import com.intellij.openapi.vfs.newvfs.AttributeInputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSAttributeAccessor
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.constCopier
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogContext
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsChronicle.LookupResult.Companion.toState
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.Companion.bind
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.Companion.fmap
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.fmap
import com.intellij.util.io.SimpleStringPersistentEnumerator
import com.intellij.util.io.UnsyncByteArrayInputStream
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class NotAvailableVfsSnapshot(point: OperationLogStorage.Iterator) : VfsSnapshot {
  override val point: () -> OperationLogStorage.Iterator = point.constCopier()

  override fun getFileById(fileId: Int): VirtualFileSnapshot {
    return NotAvailableVirtualFileSnapshot(fileId)
  }

  class NotAvailableVirtualFileSnapshot(override val fileId: Int) : VirtualFileSnapshot {
    override val nameId: Property<Int> = NotAvailableProp()
    override val parentId: Property<Int> = NotAvailableProp()
    override val length: Property<Long> = NotAvailableProp()
    override val timestamp: Property<Long> = NotAvailableProp()
    override val flags: Property<Int> = NotAvailableProp()
    override val contentRecordId: Property<Int> = NotAvailableProp()
    override val attributesRecordId: Property<Int> = NotAvailableProp()
    override val name: Property<String> = NotAvailableProp()
    override val parent: Property<VirtualFileSnapshot?> = NotAvailableProp()
    override fun getContent(): State.DefinedState<ByteArray> = State.notAvailable()
    override fun readAttribute(fileAttribute: FileAttribute): State.DefinedState<AttributeInputStream?> = State.notAvailable()

    class NotAvailableProp<T> : Property<T>() {
      override fun compute(): State.DefinedState<T> = State.notAvailable()
    }
  }
}

/*
  TODO
  Another, more efficient implementation for the case when we are interested not in just a couple of file's properties, but in almost all of
  them: properties can be calculated in a single traversal in which we populate all file's properties (not only the one that was requested)
  and stop when we've found the one that we were requested to find. It is important here that the computation of following property requests
  can continue the traversal from the previous stop point. Cache reuse should also be possible (though, probably, we'll need access to
  various snapshots, not just the closest one).
 */

class CacheAwareVfsSnapshot(
  point: OperationLogStorage.Iterator,
  private val logContext: VfsLogContext,
  private val id2filename: (Int) -> String?,
  private val attributeEnumerator: SimpleStringPersistentEnumerator,
  private val payloadReader: (PayloadRef) -> State.DefinedState<ByteArray>,
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
      VfsChronicle.lookupNameId(iter, fileId, condition = { iter != stopIter }).toState()
    }
    override val name: Property<String> = nameId.bind { id2filename(it)?.let(State::ready) ?: State.notAvailable() }

    override val parentId: Property<Int> = CacheAwareProp(VirtualFileSnapshot::parentId) { stopIter, iter ->
      VfsChronicle.lookupParentId(iter, fileId, condition = { iter != stopIter }).toState()
    }
    override val parent: Property<VirtualFileSnapshot?> = parentId.fmap { id -> if (id == 0) null else getFileById(id) }

    override val length: Property<Long> = CacheAwareProp(VirtualFileSnapshot::length) { stopIter, iter ->
      VfsChronicle.lookupLength(iter, fileId, condition = { iter != stopIter }).toState()
    }
    override val timestamp: Property<Long> = CacheAwareProp(VirtualFileSnapshot::timestamp) { stopIter, iter ->
      VfsChronicle.lookupTimestamp(iter, fileId, condition = { iter != stopIter }).toState()
    }
    override val flags: Property<Int> = CacheAwareProp(VirtualFileSnapshot::flags) { stopIter, iter ->
      VfsChronicle.lookupFlags(iter, fileId, condition = { iter != stopIter }).toState()
    }
    override val contentRecordId: Property<Int> = CacheAwareProp(VirtualFileSnapshot::contentRecordId) { stopIter, iter ->
      VfsChronicle.lookupContentRecordId(iter, fileId, condition = { iter != stopIter }).toState()
    }
    override val attributesRecordId: Property<Int> = CacheAwareProp(VirtualFileSnapshot::attributesRecordId) { stopIter, iter ->
      VfsChronicle.lookupAttributeRecordId(iter, fileId, condition = { iter != stopIter }).toState()
    }

    override fun getContent(): State.DefinedState<ByteArray> =
      contentRecordId.bind {
        VfsChronicle.restoreContent(point(), it, payloadReader)
      }.observeState()

    override fun readAttribute(fileAttribute: FileAttribute): State.DefinedState<AttributeInputStream?> {
      val attrId = logContext.stringEnumerator.enumerate(fileAttribute.id)
      val attrData = VfsChronicle.lookupAttributeData(point(), fileId, attrId)
      if (!attrData.found) return State.notAvailable()
      val payloadRef = attrData.value
      if (payloadRef == null) return State.Ready(null)
      return payloadReader(payloadRef).fmap {
        PersistentFSAttributeAccessor.readAttributeImpl(
          fileAttribute,
          AttributeInputStream(UnsyncByteArrayInputStream(it), attributeEnumerator)
        )
      }
    }

    private inner class CacheAwareProp<T>(
      private val accessProp: VirtualFileSnapshot.() -> Property<T>,
      private val queryLog: (stopIter: OperationLogStorage.Iterator?, iter: OperationLogStorage.Iterator) -> State.DefinedState<T>
    ) : Property<T>() {
      override fun compute(): State.DefinedState<T> {
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