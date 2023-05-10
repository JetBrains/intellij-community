// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine

import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.constCopier
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsChronicle.LookupResult.Companion.toState
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.Companion.bind
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.Companion.fmap
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State
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

    class NotAvailableProp<T> : Property<T>() {
      override fun compute(): State.DefinedState<T> = State.notAvailable()
    }
  }
}

class CacheAwareVfsSnapshot(
  point: OperationLogStorage.Iterator,
  private val id2name: (Int) -> String?,
  private val payloadReader: (PayloadRef) -> ByteArray?,
  private val getPrecedingCachedSnapshot: (point: OperationLogStorage.Iterator) -> VfsSnapshot?,
  private val oracle: VfsStateOracle? = null
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
    // TODO VfsChronicle.lookup* might throw on Invalid, gotta catch

    override val nameId: Property<Int> = CacheAwareProp(VirtualFileSnapshot::nameId) { stopIter, iter ->
      VfsChronicle.lookupNameId(iter, fileId, condition = { iter != stopIter }).toState()
    }
    override val name: Property<String> = nameId.bind { id2name(it)?.let(State::ready) ?: State.notAvailable() }

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

    private inner class CacheAwareProp<T>(
      private val accessProp: VirtualFileSnapshot.() -> Property<T>,
      private val queryLog: (stopIter: OperationLogStorage.Iterator?, iter: OperationLogStorage.Iterator) -> State.DefinedState<T>
    ) : Property<T>() {
      override fun compute(): State.DefinedState<T> {
        if (oracle != null) {
          val oracleSnapshot = oracle.invoke(point()) // kotlin analysis is bugged, have to use .invoke, KTJ-25463
          oracleSnapshot?.getFileById(fileId)?.accessProp()
            ?.observe(onNotAvailable = { /* no value from oracle */ }) { return State.Ready(it) }
        }
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