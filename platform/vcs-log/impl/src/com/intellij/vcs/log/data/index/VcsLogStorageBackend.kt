// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogTextFilter
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.impl.VcsLogIndexer
import it.unimi.dsi.fastutil.ints.IntSet
import java.io.IOException
import java.util.function.IntConsumer
import java.util.function.IntFunction
import java.util.function.ToIntFunction

internal interface VcsLogStorageBackend {
  val isEmpty: Boolean

  /**
   * null if not applicable
   */
  val trigramsEmpty: Boolean?
    get() = null

  var isFresh: Boolean

  fun getMessage(commitId: Int): String?

  fun getCommitterOrAuthor(commitId: Int, getUserById: IntFunction<VcsUser>, getAuthorForCommit: IntFunction<VcsUser>): VcsUser?

  fun getTimestamp(commitId: Int): LongArray?

  fun getParent(commitId: Int): IntArray?

  @Throws(IOException::class)
  fun containsCommit(commitId: Int): Boolean

  @Throws(IOException::class)
  fun collectMissingCommits(commitIds: IntSet, missing: IntSet)

  @Throws(IOException::class)
  fun processMessages(processor: (Int, String) -> Boolean)

  // todo move to mutator
  @Throws(IOException::class)
  fun putRename(parent: Int, child: Int, renames: IntArray)

  fun forceRenameMap()

  fun getRename(parent: Int, child: Int): IntArray?

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
  fun putCommit(commitId: Int, details: VcsLogIndexer.CompressedDetails, userToId: ToIntFunction<VcsUser>)

  fun putParents(commitId: Int, parents: List<Hash>, hashToId: ToIntFunction<Hash>)

  fun flush()

  fun close(performCommit: Boolean)

  fun putRename(parent: Int, child: Int, renames: IntArray)
}