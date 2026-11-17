// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registry of files that a refresh scan has read, but whose events are not applied yet (the `actualizing` state)
 *
 * Each session keeps its own [Batch]. A file is `actualizing` while at least one registered batch contains it.
 *
 * Registry is used by a synchronous [RefreshSession], which needs to know `actualizing` files, with changes in-flight.
 */
@ApiStatus.Internal
class InFlightScans {
  // there are only a few sessions at a time: copy-on-write keeps the lookups lock-free
  private val batches = CopyOnWriteArrayList<Batch>()

  fun isNotEmpty(): Boolean = !batches.isEmpty()

  /** Registers a new empty batch for a session */
  fun begin(): Batch = Batch(this).also { batches.add(it) }

  /** Forgets the batch and its files. Safe to call twice */
  fun release(batch: Batch) {
    if (batches.remove(batch)) {
      batch.clear()
    }
  }

  /** Forgets all batches, e.g. when the refresh queue is disposed */
  fun releaseAll() {
    for (batch in batches.toList()) {
      release(batch)
    }
  }

  /** @return `true` if some session has scanned the file but has not applied the events yet */
  fun isActualizing(fileId: Int): Boolean = batches.any { it.contains(fileId) }

  /**
   * @return `true` if some other session has scanned the file and has not applied the events yet. The files that the owner
   * of [own] has scanned itself are not counted: it has read them from the disk, so it does not need to read them again
   */
  fun isActualizingElsewhere(fileId: Int, own: Batch): Boolean = !own.contains(fileId) && isActualizing(fileId)

  /** Files that one session has scanned and has not applied yet. The scan workers add to it concurrently */
  class Batch internal constructor(private val owner: InFlightScans) {
    // TODO RC: concurrent hashset<Int> is quite an overkill here. ConcurrentBitSet may be a better option
    private val fileIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /** Call it before `markClean`, so the file never looks clean while its events are still in flight */
    fun add(fileId: Int) {
      fileIds.add(fileId)
    }

    /** Same as [InFlightScans.release], so a session does not need the registry at the end of its life */
    fun release() = owner.release(this)

    internal fun contains(fileId: Int): Boolean = fileIds.contains(fileId)

    internal fun clear() = fileIds.clear()
  }
}