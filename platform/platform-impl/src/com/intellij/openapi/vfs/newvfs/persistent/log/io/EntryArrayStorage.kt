// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.io

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
interface EntryArrayStorage<Entry, State: EntryArrayStorage.PersistentState<Entry>>: AutoCloseable {
  fun performUpdate(
    fromState: State,
    newSize: Int,
    updatedEntries: Map<Int, Entry>,
    checkCancelled: () -> Unit = { }
  ): State = performUpdate(fromState, newSize, updatedEntries.keys, { updatedEntries[it]!! }, checkCancelled)

  /**
   * In case the size increases, [updatedEntryIds] must contain elements that correspond to the newly created ids
   * @param getUpdatedEntry can be invoked only on elements of [updatedEntryIds]
   * @param checkCancelled should throw an exception in case operation should be cancelled
   */
  fun performUpdate(
    fromState: State,
    newSize: Int,
    updatedEntryIds: Set<Int>,
    getUpdatedEntry: (Int) -> Entry,
    checkCancelled: () -> Unit = { }
  ): State

  fun clearObsoleteFiles(currentState: State): StorageSpaceConsumptionStatistics?

  fun emptyState(): State

  val stateExternalizer: EntryExternalizer<State>

  /**
   * entry size must be positive for any entry of array
   * RandomAccessBuffers are entry-local, i.e. entry's first byte is at position 0
   */
  interface EntryExternalizer<E> {
    fun getEntrySize(entry: E): Long
    fun getEntrySize(readBuffer: RandomAccessReadBuffer): Long
    fun deserialize(readBuffer: RandomAccessReadBuffer): E
    fun serialize(writeBuffer: RandomAccessWriteBuffer, entry: E)
  }

  interface ConstSizeEntryExternalizer<E> : EntryExternalizer<E> {
    val entrySize: Long
    override fun getEntrySize(readBuffer: RandomAccessReadBuffer): Long = entrySize
    override fun getEntrySize(entry: E): Long = entrySize
  }

  interface PersistentState<Entry> {
    /**
     * Number of entries in the EntryArray, entries have ids in range [0...size) as in a usual array
     */
    val size: Int

    fun getEntry(entryId: Int): Entry
    fun getEntrySize(entryId: Int): Long
  }

  data class StorageSpaceConsumptionStatistics(
    val obsoleteFilesRemoved: Int,
    val spaceFreedUpBytes: Long,
    val currentFilesInUse: Int,
    val currentSpaceConsumptionBytes: Long,
  ) {
    companion object {
      operator fun StorageSpaceConsumptionStatistics.plus(rhs: StorageSpaceConsumptionStatistics): StorageSpaceConsumptionStatistics =
        StorageSpaceConsumptionStatistics(
          obsoleteFilesRemoved + rhs.obsoleteFilesRemoved,
          spaceFreedUpBytes + rhs.spaceFreedUpBytes,
          currentFilesInUse + rhs.currentFilesInUse,
          currentSpaceConsumptionBytes + rhs.currentSpaceConsumptionBytes
        )
    }
  }
}