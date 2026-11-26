// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.util.iterateCommitsWithPrefix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/** What the loaded log knows about a hash, see [getHashStatus]. */
@ApiStatus.Internal
enum class VcsLogHashStatus {
  /** A root takes the hash as a full hash, and the log storage holds the commit. */
  PRESENT,

  /** No root holds the hash as a full hash, and no root takes it as a short hash. */
  ABSENT,

  /** No root holds the hash as a full hash, and a root takes it as a short hash. Only a prefix search can find the commit. */
  PREFIX,

  /** The log is not loaded, or its graph data is not full. */
  UNKNOWN,
}

/**
 * Checks the hexadecimal [hash] against the log storage of [roots].
 *
 * The check does not wait for the log to load, and it does no prefix search. So it is cheap enough for each hovered line.
 * A storage lookup can block on I/O.
 */
@ApiStatus.Internal
@RequiresBackgroundThread
fun VcsProjectLog.getHashStatus(roots: Collection<VirtualFile>, hash: String): VcsLogHashStatus {
  val data = dataManager?.takeIf { it.graphData.isFull } ?: return VcsLogHashStatus.UNKNOWN
  var takesShortHash = false
  for (root in roots) {
    val provider = data.logProviders[root] ?: continue
    if (!provider.isFullHash(root, hash)) {
      takesShortHash = true
    }
    else if (data.storage.containsCommit(CommitId(HashImpl.build(hash), root))) {
      return VcsLogHashStatus.PRESENT
    }
  }
  return if (takesShortHash) VcsLogHashStatus.PREFIX else VcsLogHashStatus.ABSENT
}

/** The result of [showCommitWithPrefix]. */
@ApiStatus.Internal
enum class VcsLogShowCommitResult {
  /** The log shows the commit. */
  SHOWN,

  /** The log storage of the roots holds no matching commit. */
  NOT_FOUND,

  /** The log storage holds a matching commit, but no log can show it. */
  NOT_SHOWN,
}

/**
 * Shows the commit of the hexadecimal [hash] under [roots], and waits for the log to load. The [hash] can be a prefix.
 *
 * The log shows the first match, see [VcsProjectLog.showRevision]. A short hash of 7 digits has a small chance of a second
 * match, so the function does not look for one. The function shows no progress of its own.
 */
@ApiStatus.Internal
suspend fun VcsProjectLog.showCommitWithPrefix(roots: Collection<VirtualFile>, hash: String): VcsLogShowCommitResult {
  val manager = init(true) ?: return VcsLogShowCommitResult.NOT_FOUND
  // `waitForRefresh` schedules a log update, which runs on the EDT.
  withContext(Dispatchers.EDT) { manager.waitForRefresh() }
  val data = manager.dataManager
  val providers = data.logProviders.filterKeys { it in roots }
  val match = withContext(Dispatchers.IO) {
    var first: CommitId? = null
    data.storage.iterateCommitsWithPrefix(hash, providers) { commitId ->
      first = commitId
      false
    }
    first
  } ?: return VcsLogShowCommitResult.NOT_FOUND
  // `showRevision` uses a log tab that holds the commit, or a tab without filters.
  val shown = showRevision(match.root, match.hash, null)
  return if (shown) VcsLogShowCommitResult.SHOWN else VcsLogShowCommitResult.NOT_SHOWN
}
