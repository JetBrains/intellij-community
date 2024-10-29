// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.permanent

import com.intellij.vcs.log.graph.GraphCommit
import com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo
import com.intellij.vcs.log.graph.utils.IntList
import com.intellij.vcs.log.graph.utils.TimestampGetter
import com.intellij.vcs.log.graph.utils.impl.CompressedIntList
import com.intellij.vcs.log.graph.utils.impl.IntTimestampGetter
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
class PermanentCommitsInfoImpl<CommitId : Any> private constructor(internal val timestampGetter: TimestampGetter,
                                                                   private val commitIdIndexes: List<CommitId>,
                                                                   private val notLoadedCommits: Int2ObjectMap<CommitId>) : PermanentCommitsInfo<CommitId> {
  override fun getCommitId(nodeId: Int): CommitId {
    if (nodeId < 0) return notLoadedCommits[nodeId]
    return commitIdIndexes[nodeId]
  }

  override fun getTimestamp(nodeId: Int): Long {
    if (nodeId < 0) return 0
    return timestampGetter.getTimestamp(nodeId)
  }

  // todo optimize with special map
  override fun getNodeId(commitId: CommitId): Int {
    val indexOf = commitIdIndexes.indexOf(commitId)
    if (indexOf != -1) return indexOf

    return getNotLoadNodeId(commitId)
  }

  private fun getNotLoadNodeId(commitId: CommitId): Int {
    for (entry in notLoadedCommits.int2ObjectEntrySet()) {
      if (entry.value == commitId) {
        return entry.intKey
      }
    }
    return -1
  }

  fun convertToCommitIdList(commitIndexes: Collection<Int>): List<CommitId> {
    return commitIndexes.map(this::getCommitId)
  }

  fun convertToCommitIdSet(commitIndexes: Collection<Int>): Set<CommitId> {
    return commitIndexes.mapTo(HashSet(), this::getCommitId)
  }

  override fun convertToNodeIds(commitIds: Collection<CommitId>): Set<Int> {
    return convertToNodeIds(commitIds, false)
  }

  internal fun convertToNodeIds(commitIds: Collection<CommitId>, skipNotLoadedCommits: Boolean): IntSet {
    val result = IntOpenHashSet()
    for (i in commitIdIndexes.indices) {
      val commitId = commitIdIndexes[i]
      if (commitIds.contains(commitId)) {
        result.add(i)
      }
    }
    if (!skipNotLoadedCommits) {
      for (entry in notLoadedCommits.int2ObjectEntrySet()) {
        if (commitIds.contains(entry.value)) {
          result.add(entry.intKey)
        }
      }
    }
    return result
  }

  fun containsAll(commitIds: Collection<CommitId>): Boolean {
    return commitIdIndexes.containsAll(commitIds)
  }

  companion object {
    @JvmStatic
    fun <CommitId : Any> newInstance(graphCommits: List<GraphCommit<CommitId>>,
                                     notLoadedCommits: Int2ObjectMap<CommitId>): PermanentCommitsInfoImpl<CommitId> {
      val isIntegerCase = !graphCommits.isEmpty() && graphCommits[0].id is Int

      val commitIdIndex = if (isIntegerCase) createCompressedIntList(graphCommits as List<GraphCommit<Int>>) as List<CommitId>
      else graphCommits.map { it.id }

      val timestampGetter = createTimestampGetter(graphCommits)
      return PermanentCommitsInfoImpl(timestampGetter, commitIdIndex, notLoadedCommits)
    }

    @JvmStatic
    fun <CommitId> createTimestampGetter(graphCommits: List<GraphCommit<CommitId>>): IntTimestampGetter {
      return IntTimestampGetter.newInstance(object : TimestampGetter {
        override fun size() = graphCommits.size
        override fun getTimestamp(index: Int) = graphCommits[index].timestamp
      })
    }

    private fun createCompressedIntList(graphCommits: List<GraphCommit<Int>>): List<Int> {
      val compressedIntList = CompressedIntList.newInstance(object : IntList {
        override fun size() = graphCommits.size
        override fun get(index: Int): Int = graphCommits[index].id
      }, 30)
      return object : AbstractList<Int>() {
        override val size get() = compressedIntList.size()
        override fun get(index: Int) = compressedIntList[index]
      }
    }
  }
}
