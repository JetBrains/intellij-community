// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log

import org.jetbrains.annotations.ApiStatus

/**
 * Represents a set of stored references for a single VCS root in the VCS log.
 */
@ApiStatus.NonExtendable
@ApiStatus.Experimental
interface VcsLogRootStoredRefs : VcsRefsContainer {
  fun contains(index: VcsLogCommitStorageIndex): Boolean

  /**
   * @return all references for the given commit index.
   */
  fun refsToCommit(index: VcsLogCommitStorageIndex): List<VcsRef>

  fun getRefsIndexes(): Collection<VcsLogCommitStorageIndex>
}