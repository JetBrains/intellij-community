// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

enum class GitLabGQLQuery(val filePath: String) {
  GET_CURRENT_USER("graphql/query/getCurrentUser.graphql"),
  GET_MERGE_REQUEST("graphql/query/getMergeRequest.graphql"),
  GET_MERGE_REQUEST_DISCUSSIONS("graphql/query/getMergeRequestDiscussions.graphql"),
  GET_PROJECT_LABELS("graphql/query/getProjectLabels.graphql"),

  TOGGLE_MERGE_REQUEST_DISCUSSION_RESOLVE("graphql/query/toggleMergeRequestDiscussionResolve.graphql"),

  CREATE_NOTE("graphql/query/createNote.graphql"),
  CREATE_DIFF_NOTE("graphql/query/createDiffNote.graphql"),
  CREATE_REPLY_NOTE("graphql/query/createReplyNote.graphql"),
  UPDATE_NOTE("graphql/query/updateNote.graphql"),
  DESTROY_NOTE("graphql/query/destroyNote.graphql"),

  MERGE_REQUEST_ACCEPT("graphql/query/mergeRequestAccept.graphql"),
  MERGE_REQUEST_SET_DRAFT("graphql/query/mergeRequestSetDraft.graphql"),
  MERGE_REQUEST_SET_REVIEWERS("graphql/query/mergeRequestSetReviewers.graphql"),
  MERGE_REQUEST_UPDATE("graphql/query/mergeRequestUpdate.graphql")
}
