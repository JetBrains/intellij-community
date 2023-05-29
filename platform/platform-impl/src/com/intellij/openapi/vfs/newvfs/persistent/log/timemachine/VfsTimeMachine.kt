// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogContext
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State
import com.intellij.util.io.SimpleStringPersistentEnumerator
import java.lang.ref.SoftReference

interface VfsTimeMachine: VfsStateOracle {
  override fun getSnapshot(point: OperationLogStorage.Iterator): VfsSnapshot
}

class VfsTimeMachineImpl(
  private val vfsLogContext: VfsLogContext,
  private val id2filename: (Int) -> String?,
  private val attributeEnumerator: SimpleStringPersistentEnumerator,
  private val payloadReader: (PayloadRef) -> State.DefinedState<ByteArray>
): VfsTimeMachine {
  private val cache = sortedMapOf<Long, SoftReference<VfsSnapshot>>()
  private val zeroLayer = NotAvailableVfsSnapshot(vfsLogContext.operationLogStorage.begin()) // hard-reference so it won't be GCed from cache

  init {
    cache[zeroLayer.point().getPosition()] = SoftReference(zeroLayer)
  }

  override fun getSnapshot(point: OperationLogStorage.Iterator): VfsSnapshot = synchronized(this) {
    cache[point.getPosition()]?.get()?.let { return it }
    val snapshot = CacheAwareVfsSnapshot(point,
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