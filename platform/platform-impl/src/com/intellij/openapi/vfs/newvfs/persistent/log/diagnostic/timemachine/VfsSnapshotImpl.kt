// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine

import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.constCopier
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
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

  override fun getFileByIdIfExists(fileId: Int) = getFileById(fileId)

  class NotAvailableVirtualFileSnapshot(override val fileId: Int) : VirtualFileSnapshot {
    override val nameId = NotAvailableProp<Int>()
    override val name = NotAvailableProp<String>()
    override val parentId = NotAvailableProp<Int>()
    override val parent = NotAvailableProp<VirtualFileSnapshot?>()

    class NotAvailableProp<T> : Property<T>() {
      override fun compute(): State.DefinedState<T> = State.NotAvailable()
    }
  }
}

class CacheAwareVfsSnapshot(
  point: OperationLogStorage.Iterator,
  private val id2name: (Int) -> String?,
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

  override fun getFileByIdIfExists(fileId: Int): VirtualFileSnapshot? = fileCache.get(fileId)

  inner class CacheAwareVirtualFileSnapshot(override val fileId: Int) : VirtualFileSnapshot {
    override val nameId: Property<Int> = CacheAwareProp(VirtualFileSnapshot::nameId) { stopIter, iter ->
      VfsChronicle.lookupNameId(iter, fileId, condition = { iter != stopIter }).toState() // TODO might throw on Invalid, gotta catch
    }

    override val name: Property<String> = nameId.bind { id2name(it)?.let { name -> State.Ready(name) } ?: State.NotAvailable() }

    override val parentId: Property<Int> = CacheAwareProp(VirtualFileSnapshot::parentId) { stopIter, iter ->
      VfsChronicle.lookupParentId(iter, fileId, condition = { iter != stopIter }).toState() // TODO might throw on Invalid, gotta catch
    }

    override val parent: Property<VirtualFileSnapshot?> = parentId.fmap { id ->
      if (id == 0) null else getFileById(id)
    }

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