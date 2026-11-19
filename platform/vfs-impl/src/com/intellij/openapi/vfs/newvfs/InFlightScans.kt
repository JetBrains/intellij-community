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
 *
 * [isEnabled] switches the feature off: it is asked once per [begin], and a disabled registry hands out [Batch.NOOP]. Such a session
 * registers no files (that is the bookkeeping cost: an id per scanned file), and it does not see any file as `actualizing` elsewhere.
 */
@ApiStatus.Internal
class InFlightScans(private val isEnabled: () -> Boolean = { true }) {
  // there are only a few sessions at a time: copy-on-write keeps the lookups lock-free
  private val batches = CopyOnWriteArrayList<BatchImpl>()

  fun isNotEmpty(): Boolean = !batches.isEmpty()

  /** Registers a new empty batch for a session */
  fun begin(): Batch = if (isEnabled()) BatchImpl(this).also { batches.add(it) } else Batch.NOOP

  /** Forgets the batch and its files. Safe to call twice */
  fun release(batch: Batch) {
    if (batch is BatchImpl && batches.remove(batch)) {
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
   * of [own] has scanned itself are not counted: it has read them from the disk, so it does not need to read them again.
   * The owner of [Batch.NOOP] does not track files, so for it the answer is always `false`
   */
  fun isActualizingElsewhere(fileId: Int, own: Batch): Boolean = own !== Batch.NOOP && !own.contains(fileId) && isActualizing(fileId)

  /** Files that one session has scanned and has not applied yet. The scan workers add to it concurrently */
  interface Batch {
    /** Call it before `markClean`, so the file never looks clean while its events are still in flight */
    fun add(fileId: Int)

    /** Same as [InFlightScans.release], so a session does not need the registry at the end of its life */
    fun release()

    fun contains(fileId: Int): Boolean

    companion object {
      /** The batch of a session that does not track its files: it registers nothing and holds no file */
      val NOOP: Batch = object : Batch {
        override fun add(fileId: Int) {}

        override fun release() {}

        override fun contains(fileId: Int): Boolean = false
      }
    }
  }

  private class BatchImpl(private val owner: InFlightScans) : Batch {
    // TODO RC: concurrent hashset<Int> is quite an overkill here. ConcurrentBitSet may be a better option
    private val fileIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    override fun add(fileId: Int) {
      fileIds.add(fileId)
    }

    override fun release() = owner.release(this)

    override fun contains(fileId: Int): Boolean = fileIds.contains(fileId)

    fun clear() = fileIds.clear()
  }
}