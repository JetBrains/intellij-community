// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

enum class GHPullRequestMergeStateStatus {

  //The head ref is out of date.
  BEHIND,

  //The merge is blocked.
  BLOCKED,

  //Mergeable and passing commit status.
  CLEAN,

  //The merge commit cannot be cleanly created.
  DIRTY,

  //The merge is blocked due to the pull request being a draft.
  DRAFT,

  //Mergeable with passing commit status and pre-receive hooks.
  HAS_HOOKS,

  //The state cannot currently be determined.
  UNKNOWN,

  //Mergeable with non-passing commit status.
  UNSTABLE;

  fun canMerge(): Boolean = this == CLEAN || this == HAS_HOOKS || this == UNSTABLE
  fun adminCanMerge(): Boolean = canMerge() || this == BEHIND || this == BLOCKED
}
