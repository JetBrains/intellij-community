// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SmartList
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.VcsLogRefsOfSingleRoot
import com.intellij.vcs.log.VcsRef
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import java.util.function.IntConsumer

internal class CompressedRefs(refs: Set<VcsRef>, private val myStorage: VcsLogStorage) : VcsLogRefsOfSingleRoot {
  // maps each commit id to the list of tag ids on this commit
  private val tags: Int2ObjectMap<IntArrayList> = Int2ObjectOpenHashMap()

  // maps each commit id to the list of branches on this commit
  private val branches: Int2ObjectMap<MutableCollection<VcsRef>> = Int2ObjectOpenHashMap()

  init {
    var root: VirtualFile? = null
    for (ref in refs) {
      assert(root == null || root == ref.root) { "All references are supposed to be from the single root" }
      root = ref.root

      val index = myStorage.getCommitIndex(ref.commitHash, ref.root)
      if (ref.type.isBranch) {
        (branches.computeIfAbsent(index) { SmartList() }).add(ref)
      }
      else {
        val refIndex = myStorage.getRefIndex(ref)
        if (refIndex != VcsLogStorageImpl.NO_INDEX) {
          tags.computeIfAbsent(index) { IntArrayList() }.add(refIndex)
        }
      }
    }
    for (list in tags.values) {
      list.trim()
    }
  }

  override fun contains(index: VcsLogCommitStorageIndex): Boolean {
    return branches.containsKey(index) || tags.containsKey(index)
  }

  override fun refsToCommit(index: VcsLogCommitStorageIndex): SmartList<VcsRef> {
    val result = SmartList<VcsRef>()
    branches[index]?.let { result.addAll(it) }
    tags[index]?.forEach { tag ->
      val ref = myStorage.getVcsRef(tag)
      if (ref != null) {
        result.add(ref)
      }
      else {
        LOG.error("Could not find a tag by id $tag at commit ${myStorage.getCommitId(index)}")
      }
    }
    return result
  }

  override fun getBranches(): Sequence<VcsRef> = branches.values.asSequence().flatMap { it.asSequence() }

  override fun getTags(): Sequence<VcsRef> = tags.values.asSequence().flatMap { tagsCollection: IntArrayList ->
    tagsCollection.asSequence().mapNotNull { myStorage.getVcsRef(it) }
  }

  override fun getRefsIndexes(): Collection<VcsLogCommitStorageIndex> {
    val result = IntOpenHashSet(branches.keys.size + tags.keys.size)
    result.addAll(branches.keys)
    result.addAll(tags.keys)
    return result
  }

  override fun forEachBranchIndex(consumer: IntConsumer) {
    branches.keys.forEach(consumer)
  }

  companion object {
    private val LOG = logger<CompressedRefs>()
  }
}
