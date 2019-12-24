// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.util

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.RefsModel
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.impl.permanent.PermanentCommitsInfoImpl
import com.intellij.vcs.log.graph.utils.exclusiveNodes
import com.intellij.vcs.log.graph.utils.subgraphDifference
import gnu.trove.TIntHashSet

fun RefsModel.findBranch(name: String, root: VirtualFile): VcsRef? = VcsLogUtil.findBranch(this, root, name)
fun DataPack.findBranch(name: String, root: VirtualFile): VcsRef? = refsModel.findBranch(name, root)

fun DataPack.subgraphDifference(withRef: VcsRef, withoutRef: VcsRef, storage: VcsLogStorage): TIntHashSet? {
  if (withoutRef.root != withRef.root) return null

  val withRefIndex = storage.getCommitIndex(withRef.commitHash, withRef.root)
  val withoutRefIndex = storage.getCommitIndex(withoutRef.commitHash, withoutRef.root)

  return subgraphDifference(withRefIndex, withoutRefIndex)
}

fun DataPack.subgraphDifference(withRefIndex: Int, withoutRefIndex: Int): TIntHashSet? {
  if (withRefIndex == withoutRefIndex) return TIntHashSet()

  @Suppress("UNCHECKED_CAST") val permanentGraphInfo = permanentGraph as? PermanentGraphInfo<Int> ?: return null

  val withRefNode = permanentGraphInfo.permanentCommitsInfo.getNodeId(withRefIndex)
  val withoutRefNode = permanentGraphInfo.permanentCommitsInfo.getNodeId(withoutRefIndex)

  if (withRefNode < 0 || withoutRefNode < 0) return null

  val withRefNodeIds = permanentGraphInfo.linearGraph.subgraphDifference(withRefNode, withoutRefNode)
  return TroveUtil.map2IntSet(withRefNodeIds) { permanentGraphInfo.permanentCommitsInfo.getCommitId(it) }
}

fun DataPack.containsAll(commits: Collection<CommitId>, storage: VcsLogStorage): Boolean {
  val commitIds = commits.map { storage.getCommitIndex(it.hash, it.root) }
  @Suppress("UNCHECKED_CAST") val permanentGraphInfo = permanentGraph as? PermanentGraphInfo<Int> ?: return false
  if (permanentGraphInfo.permanentCommitsInfo is PermanentCommitsInfoImpl<Int>) {
    return (permanentGraphInfo.permanentCommitsInfo as PermanentCommitsInfoImpl<Int>).containsAll(commitIds)
  }
  val nodeIds = permanentGraphInfo.permanentCommitsInfo.convertToNodeIds(commitIds)
  return nodeIds.size == commits.size && nodeIds.all { it >= 0 }
}

fun DataPack.exclusiveCommits(ref: VcsRef, refsModel: RefsModel, storage: VcsLogStorage): TIntHashSet? {
  val refIndex = storage.getCommitIndex(ref.commitHash, ref.root)
  @Suppress("UNCHECKED_CAST") val permanentGraphInfo = permanentGraph as? PermanentGraphInfo<Int> ?: return null
  val refNode = permanentGraphInfo.permanentCommitsInfo.getNodeId(refIndex)
  if (refNode < 0) return null

  val exclusiveNodes = permanentGraphInfo.linearGraph.exclusiveNodes(refNode) { node ->
    refsModel.isBranchHead(permanentGraphInfo.permanentCommitsInfo.getCommitId(node))
  }
  return TroveUtil.map2IntSet(exclusiveNodes) { permanentGraphInfo.permanentCommitsInfo.getCommitId(it) }
}

private fun RefsModel.isBranchHead(commitId: Int) = refsToCommit(commitId).any { it.type.isBranch }