// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

enum class GHPullRequestReviewState {
  //A review allowing the pull request to merge.
  APPROVED,
  //A review blocking the pull request from merging.
  CHANGES_REQUESTED,
  //An informational review.
  COMMENTED,
  //A review that has been dismissed.
  DISMISSED,
  //A review that has not yet been submitted.
  PENDING
}