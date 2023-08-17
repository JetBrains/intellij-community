// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

enum class GitLabGQLQuery(val filePath: String) {
  @SinceGitLab("12.5", note = "No exact version") GET_CURRENT_USER("graphql/query/getCurrentUser.graphql"),
  @SinceGitLab("10.0", note = "No exact version") GET_MERGE_REQUEST("graphql/query/getMergeRequest.graphql"),
  FIND_MERGE_REQUESTS("graphql/query/findProjectMergeRequests.graphql"),
  GET_MERGE_REQUEST_DISCUSSIONS("graphql/query/getMergeRequestDiscussions.graphql"),
  @SinceGitLab("13.1", note = "No exact version") GET_PROJECT_LABELS("graphql/query/getProjectLabels.graphql"),
  GET_MEMBER_PROJECTS("graphql/query/getMemberProjects.graphql"),

  TOGGLE_MERGE_REQUEST_DISCUSSION_RESOLVE("graphql/query/toggleMergeRequestDiscussionResolve.graphql"),

  CREATE_NOTE("graphql/query/createNote.graphql"),
  CREATE_DIFF_NOTE("graphql/query/createDiffNote.graphql"),
  CREATE_REPLY_NOTE("graphql/query/createReplyNote.graphql"),
  CREATE_SNIPPET("graphql/query/createSnippet.graphql"),
  UPDATE_NOTE("graphql/query/updateNote.graphql"),
  UPDATE_SNIPPET_BLOB("graphql/query/updateSnippetBlob.graphql"),
  DESTROY_NOTE("graphql/query/destroyNote.graphql"),

  MERGE_REQUEST_ACCEPT("graphql/query/mergeRequestAccept.graphql"),
  MERGE_REQUEST_SET_DRAFT("graphql/query/mergeRequestSetDraft.graphql"),
  MERGE_REQUEST_SET_REVIEWERS("graphql/query/mergeRequestSetReviewers.graphql"),
  MERGE_REQUEST_UPDATE("graphql/query/mergeRequestUpdate.graphql"),
  MERGE_REQUEST_REVIEWER_REREVIEW("graphql/query/mergeRequestReviewerRereview.graphql")
}
