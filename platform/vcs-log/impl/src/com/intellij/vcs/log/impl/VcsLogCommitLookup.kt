// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.CommitId
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
