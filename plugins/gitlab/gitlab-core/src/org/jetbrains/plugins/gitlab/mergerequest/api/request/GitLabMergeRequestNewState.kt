// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

enum class GitLabMergeRequestNewState {
  /**
   * Close the merge request if it is open.
   */
  CLOSED,

  /**
   * Open the merge request if it is closed.
   */
  OPEN
}