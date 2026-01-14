// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SmartList
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.VcsLogRootStoredRefs
import com.intellij.vcs.log.VcsRef
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import java.util.function.IntConsumer

internal class CompressedRefs(refs: Iterable<VcsRef>, private val storage: VcsLogStorage) : VcsLogRootStoredRefs {
  // maps each commit id to the list of tag ids on this commit
  private val tagsMapping: Int2ObjectMap<IntArrayList> = Int2ObjectOpenHashMap()
  // maps each commit id to the list of branches on this commit
  private val branchesMapping: Int2ObjectMap<MutableCollection<VcsRef>> = Int2ObjectOpenHashMap()

  init {
    var root: VirtualFile? = null
    for (ref in refs) {
      assert(root == null || root == ref.root) { "All references are supposed to be from the single root" }
      root = ref.root

      val index = storage.getCommitIndex(ref.commitHash, ref.root)
      if (ref.type.isBranch) {
        (branchesMapping.computeIfAbsent(index) { SmartList() }).add(ref)
      }
      else {
        val refIndex = storage.getRefIndex(ref)
        if (refIndex != VcsLogStorageImpl.NO_INDEX) {
          tagsMapping.computeIfAbsent(index) { IntArrayList() }.add(refIndex)
        }
      }
    }
    for (list in tagsMapping.values) {
      list.trim()
    }
  }

  override fun branches(): Sequence<VcsRef> = branchesMapping.values.asSequence().flatMap { it.asSequence() }
  override fun tags(): Sequence<VcsRef> = tagsMapping.values.asSequence().flatMap { tagsCollection: IntArrayList ->
    tagsCollection.asSequence().mapNotNull { storage.getVcsRef(it) }
  }

  override fun contains(index: VcsLogCommitStorageIndex): Boolean {
    return branchesMapping.containsKey(index) || tagsMapping.containsKey(index)
  }

  override fun refsToCommit(index: VcsLogCommitStorageIndex): SmartList<VcsRef> {
    val result = SmartList<VcsRef>()
    branchesMapping[index]?.let { result.addAll(it) }
    tagsMapping[index]?.forEach { tag ->
      val ref = storage.getVcsRef(tag)
      if (ref != null) {
        result.add(ref)
      }
      else {
        LOG.error("Could not find a tag by id $tag at commit ${storage.getCommitId(index)}")
      }
    }
    return result
  }

  override fun getRefsIndexes(): Collection<VcsLogCommitStorageIndex> {
    val result = IntOpenHashSet(branchesMapping.keys.size + tagsMapping.keys.size)
    result.addAll(branchesMapping.keys)
    result.addAll(tagsMapping.keys)
    return result
  }

  override fun forEachBranchIndex(consumer: IntConsumer) {
    branchesMapping.keys.forEach(consumer)
  }

  companion object {
    private val LOG = logger<CompressedRefs>()
  }
}
