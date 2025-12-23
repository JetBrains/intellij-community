// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log

import org.jetbrains.annotations.ApiStatus
import java.util.function.IntConsumer

/**
 * Represents a set of references for a single VCS root in the VCS log.
 */
@ApiStatus.NonExtendable
interface VcsLogRefsOfSingleRoot {
  fun contains(index: VcsLogCommitStorageIndex): Boolean

  /**
   * @return all references for the given commit index.
   */
  fun refsToCommit(index: VcsLogCommitStorageIndex): List<VcsRef>

  fun getBranches(): Sequence<VcsRef>
  fun getTags(): Sequence<VcsRef>
  fun getRefs(): Sequence<VcsRef> = getBranches() + getTags()

  fun getRefsIndexes(): Collection<VcsLogCommitStorageIndex>

  fun forEachBranchIndex(consumer: IntConsumer)
}