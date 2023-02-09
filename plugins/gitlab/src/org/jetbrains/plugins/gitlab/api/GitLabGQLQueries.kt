// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

object GitLabGQLQueries {
  const val getCurrentUser = "graphql/query/getCurrentUser.graphql"
  const val getMergeRequest = "graphql/query/getMergeRequest.graphql"
  const val getMergeRequestDiscussions = "graphql/query/getMergeRequestDiscussions.graphql"
  const val getProjectLabels = "graphql/query/getProjectLabels.graphql"
  const val getProjectMembers = "graphql/query/getProjectMembers.graphql"

  const val toggleMergeRequestDiscussionResolve = "graphql/query/toggleMergeRequestDiscussionResolve.graphql"

  const val createNote = "graphql/query/createNote.graphql"
  const val createDiffNote = "graphql/query/createDiffNote.graphql"
  const val createReplyNote = "graphql/query/createReplyNote.graphql"
  const val updateNote = "graphql/query/updateNote.graphql"
  const val destroyNote = "graphql/query/destroyNote.graphql"

  const val mergeRequestAccept = "graphql/query/mergeRequestAccept.graphql"
  const val mergeRequestSetDraft = "graphql/query/mergeRequestSetDraft.graphql"
  const val mergeRequestSetReviewers = "graphql/query/mergeRequestSetReviewers.graphql"
  const val mergeRequestUpdate = "graphql/query/mergeRequestUpdate.graphql"
}