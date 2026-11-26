// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.util

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.CommitIdByStringCondition
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.impl.HashImpl

/**
 * Gives [consumer] each commit of the roots of [logProviders] whose hash starts with [hashPrefix], until [consumer] returns false.
 * A root that takes [hashPrefix] as a full hash checks it directly. The prefix search over the storage runs only when no root
 * holds the full hash and some root takes [hashPrefix] as a short hash. The prefix search checks for cancellation at each commit.
 */
internal fun VcsLogStorage.iterateCommitsWithPrefix(
  hashPrefix: String,
  logProviders: Map<VirtualFile, VcsLogProvider>,
  consumer: (CommitId) -> Boolean,
) {
  var commitFound = false
  var isFullHashInAllRoots = true
  val fullHash = HashImpl.build(hashPrefix)

  for ((root, provider) in logProviders) {
    if (!provider.isFullHash(root, hashPrefix)) {
      isFullHashInAllRoots = false
      continue
    }

    val commitId = CommitId(fullHash, root)
    if (containsCommit(commitId)) {
      commitFound = true
      if (!consumer(commitId)) return
    }
  }

  if (commitFound || isFullHashInAllRoots) return

  iterateCommits { commitId ->
    ProgressManager.checkCanceled()
    if (!CommitIdByStringCondition.matches(commitId, hashPrefix) || commitId.root !in logProviders.keys) return@iterateCommits true

    consumer(commitId)
  }
}
