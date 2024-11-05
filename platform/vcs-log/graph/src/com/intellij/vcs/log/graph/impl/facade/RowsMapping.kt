// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade

import com.intellij.vcs.log.graph.utils.TimestampGetter
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.longs.LongArrayList

internal class RowsMapping<CommitId>(size: Int, optimizeForInt: Boolean): TimestampGetter {
  @Suppress("UNCHECKED_CAST")
  private val commitIdMappingMutable: MutableList<CommitId> = if (optimizeForInt) IntArrayList(size) as MutableList<CommitId> else ArrayList<CommitId>(size)
  private val timestampsMapping = LongArrayList(size)

  val commitIdMapping: List<CommitId>
    get() = commitIdMappingMutable

  fun add(commitId: CommitId, timestamp: Long) {
    commitIdMappingMutable.add(commitId)
    timestampsMapping.add(timestamp)
  }

  fun getCommitId(row: Int): CommitId = commitIdMappingMutable[row]
  override fun getTimestamp(row: Int): Long = timestampsMapping.getLong(row)
}
