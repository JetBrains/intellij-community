// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

enum class GHPullRequestReviewEvent {
  // Submit feedback and approve merging these changes.
  APPROVE,

  // Submit general feedback without explicit approval.
  COMMENT,

  // Dismiss review so it now longer effects merging.
  DISMISS,

  // Submit feedback that must be addressed before merging.
  REQUEST_CHANGES
}
