// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

/**
 * Representation of whether a GitLab merge request can be merged.
 */
enum class GitLabMergeStatus {
  /**
   * There are conflicts between the source and target branches.
   */
  CANNOT_BE_MERGED,

  /**
   * Currently unchecked. The previous state was `CANNOT_BE_MERGED`.
   */
  CANNOT_BE_MERGED_RECHECK,

  /**
   * There are no conflicts between the source and target branches.
   */
  CAN_BE_MERGED,

  /**
   * Currently checking for mergeability.
   */
  CHECKING,

  /**
   * Merge status has not been checked.
   */
  UNCHECKED
}