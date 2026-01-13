// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.VcsLogRefsOfSingleRoot
import com.intellij.vcs.log.VcsRef
import org.jetbrains.annotations.ApiStatus
import java.util.function.IntConsumer

/**
 * Empty implementation of [VcsLogRefsOfSingleRoot] representing no references.
 */
@ApiStatus.Internal
internal data object EmptyRefs : VcsLogRefsOfSingleRoot {
  override val branches: Sequence<VcsRef> = emptySequence()
  override val tags: Sequence<VcsRef> = emptySequence()

  override fun contains(index: VcsLogCommitStorageIndex): Boolean = false

  override fun refsToCommit(index: VcsLogCommitStorageIndex): List<VcsRef> = emptyList()

  override fun getRefsIndexes(): Collection<VcsLogCommitStorageIndex> = emptySet()

  override fun forEachBranchIndex(consumer: IntConsumer) {
    // no-op
  }
}
