// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine

import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorageImpl
import java.lang.ref.SoftReference

object VfsTimeMachine {
  private val cache = sortedMapOf<Long, SoftReference<VfsSnapshot>>()

  fun getSnapshot(point: OperationLogStorage.Iterator, id2name: (Int) -> String?): VfsSnapshot = synchronized(this) {
    check(point is OperationLogStorageImpl.IteratorImpl)
    cache[point.getPosition()]?.get()?.let { return it }
    val bestToInherit = closestSnapshotToPoint(point)
    val snapshot = if (bestToInherit != null) {
      VfsSnapshotWithInheritance(point, id2name, bestToInherit)
    } else {
      VfsSnapshotBase(point, id2name)
    }
    cache[point.getPosition()] = SoftReference(snapshot)
    return snapshot
  }

  private fun closestSnapshotToPoint(point: OperationLogStorageImpl.IteratorImpl): VfsSnapshot? {
    val pos = point.getPosition()
    // TODO: can be optimized
    return cache.map { it.key to it.value.get() }.lastOrNull { it.first <= pos && it.second != null }?.second
  }
}