// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsLogRefs
import com.intellij.vcs.log.VcsRef
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.jetbrains.annotations.ApiStatus
import java.util.function.IntConsumer
import java.util.stream.Stream
import kotlin.streams.asStream

class RefsModel(val allRefsByRoot: Map<VirtualFile, CompressedRefs>, private val storage: VcsLogStorage,
                private val providers: Map<VirtualFile, VcsLogProvider>) : VcsLogRefs {
  private val bestRefForHead: Int2ObjectMap<VcsRef> = Int2ObjectOpenHashMap()
  private val rootForHead: Int2ObjectMap<VirtualFile> = Int2ObjectOpenHashMap()

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

  fun bestRefToHead(headIndex: VcsLogCommitStorageIndex): VcsRef? = bestRefForHead[headIndex]

  fun rootAtHead(headIndex: VcsLogCommitStorageIndex): VirtualFile? = rootForHead[headIndex]

  fun refsToCommit(index: VcsLogCommitStorageIndex): List<VcsRef> {
    if (allRefsByRoot.size <= 10) {
      val refs = allRefsByRoot.values.firstOrNull { it.contains(index) }
      return refs?.refsToCommit(index) ?: emptyList()
    }
    val id = storage.getCommitId(index) ?: return emptyList()
    return refsToCommit(id.root, index)
  }

  fun refsToCommit(root: VirtualFile, index: VcsLogCommitStorageIndex): List<VcsRef> {
    return allRefsByRoot[root]?.refsToCommit(index) ?: emptyList()
  }

  override fun getBranches(): Collection<VcsRef> {
    return allRefsByRoot.values.flatMapTo(mutableListOf()) {
      it.getBranches()
    }
  }

  @RequiresBackgroundThread
  override fun stream(): Stream<VcsRef> {
    return allRefsByRoot.values.asSequence().flatMap(CompressedRefs::getRefs).asStream()
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
    fun create(refs: Map<VirtualFile, CompressedRefs>, heads: Set<VcsLogCommitStorageIndex>, storage: VcsLogStorage,
               providers: Map<VirtualFile, VcsLogProvider>): RefsModel {
      val refsModel = RefsModel(refs, storage, providers)

      val remainingHeads = IntOpenHashSet(heads)
      refs.forEach { (root, refsForRoot) ->
        refsForRoot.getBranchIndexes().forEach(IntConsumer { commit ->
          refsModel.updateCacheForHead(commit, root)
          remainingHeads.remove(commit)
        })
      }
      storage.getCommitIds(remainingHeads).forEach { (head, commitId) -> refsModel.updateCacheForHead(head, commitId.root) }

      return refsModel
    }
  }
}
