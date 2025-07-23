// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.data.index.VcsLogModifiableIndex
import com.intellij.vcs.log.graph.GraphCommit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Intercepts the data loaded by the [VcsLogRefresherImpl] to be used for side activities
 */
internal interface VcsLogCommitDataConsumer {
  fun storeData(root: VirtualFile, commits: List<GraphCommit<Int>>, users: Collection<VcsUser>)

  fun storeRecentDetails(details: List<VcsCommitMetadata>)

  suspend fun flushData(onFullReload: Boolean)
}

internal class VcsLogCommitDataConsumerImpl(
  private val userRegistry: VcsUserRegistryImpl,
  private val index: VcsLogModifiableIndex,
  private val topCommitsDetailsCache: TopCommitsCache,
) : VcsLogCommitDataConsumer {
  override fun storeData(root: VirtualFile, commits: List<GraphCommit<Int>>, users: Collection<VcsUser>) {
    commits.forEach {
      index.markForIndexing(it.id, root)
    }
    userRegistry.addUsers(users)
  }

  override fun storeRecentDetails(details: List<VcsCommitMetadata>) {
    topCommitsDetailsCache.storeDetails(details)
  }

  override suspend fun flushData(onFullReload: Boolean) {
    withContext(Dispatchers.IO) {
      userRegistry.flush()
    }
    index.scheduleIndex(onFullReload)
  }
}
