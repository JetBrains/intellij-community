// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.api.permanent

interface PermanentCommitsInfo<CommitId : Any> {
  fun getCommitId(nodeId: Int): CommitId

  fun getTimestamp(nodeId: Int): Long

  fun getNodeId(commitId: CommitId): Int

  fun convertToNodeIds(commitIds: Collection<CommitId>): Set<Int>
}
