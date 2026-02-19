// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.VcsLogTextFilter
import com.intellij.vcs.log.impl.VcsLogIndexer
import com.intellij.vcs.log.util.StorageId
import it.unimi.dsi.fastutil.ints.IntSet
import java.io.IOException
import java.util.function.IntConsumer
import java.util.function.IntFunction

internal interface VcsLogStorageBackend : VcsLogUsersStorage, VcsLogPathsStorage {
  val storageId: StorageId
  var isFresh: Boolean
  val isEmpty: Boolean

  fun getMessage(commitId: VcsLogCommitStorageIndex): String?

  fun getMessages(commitIds: Collection<VcsLogCommitStorageIndex>): Map<VcsLogCommitStorageIndex, String> {
    return commitIds.mapNotNull { commitId -> getMessage(commitId)?.let { message -> commitId to message } }.toMap()
  }

  fun getTimestamp(commitId: VcsLogCommitStorageIndex): LongArray?

  fun getAuthorTime(commitIds: Collection<VcsLogCommitStorageIndex>): Map<VcsLogCommitStorageIndex, Long> {
    return commitIds.mapNotNull { getTimestamp(it)?.let { times -> it to times[0] } }.toMap()
  }

  fun getCommitTime(commitIds: Collection<VcsLogCommitStorageIndex>): Map<VcsLogCommitStorageIndex, Long> {
    return commitIds.mapNotNull { getTimestamp(it)?.let { times -> it to times[1] } }.toMap()
  }

  fun getParents(commitId: VcsLogCommitStorageIndex): IntArray?

  fun getParents(commitIds: Collection<VcsLogCommitStorageIndex>): Map<Int, List<Hash>>

  @Throws(IOException::class)
  fun containsCommit(commitId: VcsLogCommitStorageIndex): Boolean

  @Throws(IOException::class)
  fun collectMissingCommits(commitIds: IntSet): IntSet

  @Throws(IOException::class)
  fun iterateIndexedCommits(limit: Int, processor: IntFunction<Boolean>)

  @Throws(IOException::class)
  fun processMessages(processor: (VcsLogCommitStorageIndex, String) -> Boolean)

  fun createWriter(): VcsLogWriter

  fun getCommitsForSubstring(string: String,
                             candidates: IntSet?,
                             noTrigramSources: MutableList<String>,
                             consumer: IntConsumer,
                             filter: VcsLogTextFilter)

  fun markCorrupted()
}

internal interface VcsLogWriter {
  @Throws(IOException::class)
  fun putCommit(commitId: VcsLogCommitStorageIndex, details: VcsLogIndexer.CompressedDetails)
  fun flush()
  fun close(performCommit: Boolean)
  fun interrupt()
}
