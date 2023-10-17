// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

/**
 * State of synchronization between two branches from a viewpoint of the first branch
 *
 * @property incoming other branch contains commits that are not present in the first branch
 * @property outgoing first branch contains commits that are not present in the other branch
 */
data class GitBranchSyncStatus(val incoming: Boolean, val outgoing: Boolean) {
  companion object {
    val SYNCED = GitBranchSyncStatus(false, false)
  }
}
