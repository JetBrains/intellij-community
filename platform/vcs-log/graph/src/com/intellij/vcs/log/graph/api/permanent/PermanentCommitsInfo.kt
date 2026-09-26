// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.api.permanent

/**
 * Commit node id used in [PermanentCommitsInfo]
 *
 * @see [com.intellij.vcs.log.graph.api.LinearGraph.getNodeId]
 */
typealias VcsLogGraphNodeId = Int

interface PermanentCommitsInfo<CommitId : Any> {
  fun getCommitId(nodeId: VcsLogGraphNodeId): CommitId

  fun getTimestamp(nodeId: VcsLogGraphNodeId): Long

  fun getNodeId(commitId: CommitId): VcsLogGraphNodeId

  fun convertToNodeIds(commitIds: Collection<CommitId>): Set<VcsLogGraphNodeId>
}
