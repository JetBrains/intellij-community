// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine

import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import java.lang.ref.SoftReference

class VfsTimeMachine(
  zeroIterator: OperationLogStorage.Iterator,
  private val oracle: VfsStateOracle? = null,
  private val id2name: (Int) -> String?
) {
  private val cache = sortedMapOf<Long, SoftReference<VfsSnapshot>>()
  private val zeroLayer = NotAvailableVfsSnapshot(zeroIterator) // hard-reference so it won't be GCed from cache

  init {
    cache[zeroLayer.point().getPosition()] = SoftReference(zeroLayer)
  }

  fun getSnapshot(point: OperationLogStorage.Iterator): VfsSnapshot = synchronized(this) {
    cache[point.getPosition()]?.get()?.let { return it }
    val snapshot = CacheAwareVfsSnapshot(point, id2name, ::closestPrecedingCachedSnapshot, oracle)
    cache[point.getPosition()] = SoftReference(snapshot)
    return snapshot
  }

  fun closestPrecedingCachedSnapshot(point: OperationLogStorage.Iterator): VfsSnapshot? = synchronized(this) {
    val pos = point.getPosition()
    // TODO: can be optimized
    return cache.map { it.key to it.value.get() }.lastOrNull { it.first < pos && it.second != null }?.second
  }
}