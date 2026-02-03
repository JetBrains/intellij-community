// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

/**
 * State of a GitLab merge request.
 */
enum class GitLabMergeRequestState {
  /**
   * All available.
   */
  ALL,

  /**
   * In closed state.
   */
  CLOSED,

  /**
   * The discussion has been locked.
   */
  LOCKED,

  /**
   * Merge request has been merged.
   */
  MERGED,

  /**
   * In open state.
   */
  OPENED;
}

/**
 * Convert to a form accepted by the API
 */
fun GitLabMergeRequestState.asApiParameter(): String = name.lowercase()