// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.*
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.jetbrains.annotations.ApiStatus

internal class RefsModel private constructor(
  override val refsByRoot: Map<VirtualFile, VcsLogRootStoredRefs>,
  private val storage: VcsLogStorage,
  private val providers: Map<VirtualFile, VcsLogProvider>,
) : VcsLogAggregatedStoredRefs {
  private val bestRefForHead: Int2ObjectMap<VcsRef> = Int2ObjectOpenHashMap()
  private val rootForHead: Int2ObjectMap<VirtualFile> = Int2ObjectOpenHashMap()

  override fun getRefForHeadCommit(headIndex: VcsLogCommitStorageIndex): VcsRef? = bestRefForHead[headIndex]

  override fun getRootForHeadCommit(headIndex: VcsLogCommitStorageIndex): VirtualFile? = rootForHead[headIndex]

  override fun refsToCommit(index: VcsLogCommitStorageIndex): List<VcsRef> {
    if (refsByRoot.size <= 10) {
      val refs = refsByRoot.values.firstOrNull { it.contains(index) }
      return refs?.refsToCommit(index) ?: emptyList()
    }
    val id = storage.getCommitId(index) ?: return emptyList()
    return refsToCommit(id.root, index)
  }

  private fun updateCacheForHead(head: VcsLogCommitStorageIndex, root: VirtualFile) {
    rootForHead.put(head, root)

    val bestRef = refsToCommit(root, head).minWithOrNull(providers[root]!!.referenceManager.branchLayoutComparator)
    if (bestRef != null) {
      bestRefForHead.put(head, bestRef)
    }
    else {
      LOG.debug { "No references at head ${storage.getCommitId(head)}" }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(RefsModel::class.java)

    @ApiStatus.Internal
    @JvmStatic
    fun createEmptyInstance(storage: VcsLogStorage): RefsModel {
      return create(emptyMap(), emptySet(), storage, emptyMap())
    }

    @ApiStatus.Internal
    @JvmStatic
    fun create(
      refs: Map<VirtualFile, VcsLogRootStoredRefs>,
      heads: Set<VcsLogCommitStorageIndex>,
      storage: VcsLogStorage,
      providers: Map<VirtualFile, VcsLogProvider>,
    ): RefsModel {
      val refsModel = RefsModel(refs, storage, providers)

      val remainingHeads = IntOpenHashSet(heads)
      refs.forEach { (root, refsForRoot) ->
        refsForRoot.forEachBranchIndex { commit ->
          refsModel.updateCacheForHead(commit, root)
          remainingHeads.remove(commit)
        }
      }
      storage.getCommitIds(remainingHeads).forEach { (head, commitId) -> refsModel.updateCacheForHead(head, commitId.root) }

      return refsModel
    }
  }
}
