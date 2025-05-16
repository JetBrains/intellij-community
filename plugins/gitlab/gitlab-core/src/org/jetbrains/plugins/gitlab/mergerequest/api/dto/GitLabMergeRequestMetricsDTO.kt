// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment

@GraphQLFragment("/graphql/query/getMergeRequestMetrics.graphql")
data class GitLabMergeRequestMetricsDTO(
  val allMRCount: Count,
  val openMRCount: Count,
  val openAssignedMRCount: Count,
  val openAuthoredMRCount: Count,
  val openReviewAssignedMRCount: Count
) {
  data class Count(val count: Int)
}