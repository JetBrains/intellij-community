// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import com.intellij.vcs.log.Hash
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

  fun getMessage(commitId: Int): String?

  fun getMessages(commitIds: Collection<Int>): Map<Int, String> {
    return commitIds.mapNotNull { commitId -> getMessage(commitId)?.let { message -> commitId to message } }.toMap()
  }

  fun getTimestamp(commitId: Int): LongArray?

  fun getAuthorTime(commitIds: Collection<Int>): Map<Int, Long> {
    return commitIds.mapNotNull { getTimestamp(it)?.let { times -> it to times[0] } }.toMap()
  }

  fun getCommitTime(commitIds: Collection<Int>): Map<Int, Long> {
    return commitIds.mapNotNull { getTimestamp(it)?.let { times -> it to times[1] } }.toMap()
  }

  fun getParents(commitId: Int): IntArray?

  fun getParents(commitIds: Collection<Int>): Map<Int, List<Hash>>

  @Throws(IOException::class)
  fun containsCommit(commitId: Int): Boolean

  @Throws(IOException::class)
  fun collectMissingCommits(commitIds: IntSet): IntSet

  @Throws(IOException::class)
  fun iterateIndexedCommits(limit: Int, processor: IntFunction<Boolean>)

  @Throws(IOException::class)
  fun processMessages(processor: (Int, String) -> Boolean)

  fun createWriter(): VcsLogWriter

  fun getCommitsForSubstring(string: String,
                             candidates: IntSet?,
                             noTrigramSources: MutableList<String>,
                             consumer: IntConsumer,
                             filter: VcsLogTextFilter)

  fun markCorrupted()
}

interface VcsLogWriter {
  @Throws(IOException::class)
  fun putCommit(commitId: Int, details: VcsLogIndexer.CompressedDetails)
  fun flush()
  fun close(performCommit: Boolean)
  fun interrupt()
}
