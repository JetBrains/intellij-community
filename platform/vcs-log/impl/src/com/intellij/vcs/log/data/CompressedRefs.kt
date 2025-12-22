// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.google.common.base.Suppliers
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SmartList
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.VcsRef
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import java.util.stream.Stream

internal class CompressedRefs(refs: Set<VcsRef>, private val myStorage: VcsLogStorage) {
  // maps each commit id to the list of tag ids on this commit
  private val tags: Int2ObjectMap<IntArrayList> = Int2ObjectOpenHashMap()

  // maps each commit id to the list of branches on this commit
  private val branches: Int2ObjectMap<MutableCollection<VcsRef>> = Int2ObjectOpenHashMap()

  val refs: Collection<VcsRef>
    get() = object : AbstractCollection<VcsRef>() {
      private val myLoadedRefs = Suppliers.memoize { this@CompressedRefs.stream().toList() }

      override fun iterator(): Iterator<VcsRef> {
        return myLoadedRefs.get().iterator()
      }

      override val size: Int
        get() = myLoadedRefs.get().size
    }

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

  fun contains(index: VcsLogCommitStorageIndex): Boolean {
    return branches.containsKey(index) || tags.containsKey(index)
  }

  fun refsToCommit(index: VcsLogCommitStorageIndex): SmartList<VcsRef> {
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

  fun streamBranches(): Stream<VcsRef> {
    return branches.values.stream().flatMap { it.stream() }
  }

  private fun streamTags(): Stream<VcsRef> {
    return tags.values.stream().flatMapToInt { it.intStream() }.mapToObj(myStorage::getVcsRef)
  }

  fun stream(): Stream<VcsRef> {
    return Stream.concat(streamBranches(), streamTags())
  }

  fun getRefsIndexes(): IntSet {
    val result = IntOpenHashSet(branches.keys.size + tags.keys.size)
    result.addAll(branches.keys)
    result.addAll(tags.keys)
    return result
  }

  fun getBranchIndexes(): IntSet = branches.keys

  companion object {
    private val LOG = logger<CompressedRefs>()
  }
}
