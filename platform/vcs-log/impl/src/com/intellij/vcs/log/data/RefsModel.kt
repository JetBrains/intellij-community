// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.*
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.jetbrains.annotations.ApiStatus

internal class RefsModel private constructor(
  override val refsByRoot: Map<VirtualFile, VcsLogRootStoredRefs>,
  private val storage: VcsLogStorage,
  private val providers: Map<VirtualFile, VcsLogProvider>,
) : VcsLogAggregatedStoredRefs {
  private val singleRoot = refsByRoot.keys.singleOrNull()

  private val bestRefForHead: Int2ObjectMap<VcsRef> = Int2ObjectOpenHashMap()
  private val rootForHead: Int2ObjectMap<VirtualFile> = Int2ObjectOpenHashMap()

  override fun getRefForHeadCommit(headIndex: VcsLogCommitStorageIndex): VcsRef? = bestRefForHead.computeIfAbsent(headIndex) {
    val id = storage.getCommitId(headIndex) ?: return@computeIfAbsent null
    refsToCommit(headIndex).minWithOrNull(providers[id.root]!!.referenceManager.branchLayoutComparator)
  }

  override fun getRootForHeadCommit(headIndex: VcsLogCommitStorageIndex): VirtualFile? {
    return singleRoot ?: rootForHead.computeIfAbsent(headIndex) {
      storage.getCommitId(headIndex)?.root
    }
  }

  override fun refsToCommit(index: VcsLogCommitStorageIndex): List<VcsRef> {
    return when {
      singleRoot != null -> refsByRoot.getValue(singleRoot).refsToCommit(index)
      refsByRoot.size <= 10 -> {
        val refs = refsByRoot.values.firstOrNull { it.contains(index) }
        refs?.refsToCommit(index) ?: emptyList()
      }
      else -> {
        val id = storage.getCommitId(index)
        if (id != null) refsToCommit(id.root, index) else emptyList()
      }
    }
  }

  companion object {
    @ApiStatus.Internal
    @JvmStatic
    fun createEmptyInstance(storage: VcsLogStorage): RefsModel {
      return create(emptyMap(), storage, emptyMap())
    }

    @ApiStatus.Internal
    @JvmStatic
    fun create(
      refs: Map<VirtualFile, VcsLogRootStoredRefs>,
      storage: VcsLogStorage,
      providers: Map<VirtualFile, VcsLogProvider>,
    ): RefsModel = RefsModel(refs, storage, providers)
  }
}
