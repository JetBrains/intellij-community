// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

enum class GitLabGQLQuery(val filePath: String) {
  @SinceGitLab("12.0")
  GET_METADATA("graphql/query/getMetadata.graphql"),

  @SinceGitLab("12.5")
  GET_CURRENT_USER("graphql/query/getCurrentUser.graphql"),
  @SinceGitLab("12.0")
  GET_MERGE_REQUEST("graphql/query/getMergeRequest.graphql"),
  @SinceGitLab("13.1")
  FIND_MERGE_REQUESTS("graphql/query/findProjectMergeRequests.graphql"),
  @SinceGitLab("14.7")
  GET_MERGE_REQUEST_COMMITS("graphql/query/getMergeRequestCommits.graphql"),
  @SinceGitLab("12.3")
  GET_MERGE_REQUEST_DISCUSSIONS("graphql/query/getMergeRequestDiscussions.graphql"),
  @SinceGitLab("12.0")
  GET_PROJECT("graphql/query/getProject.graphql"),
  @SinceGitLab("13.1", note = "No exact version")
  GET_PROJECT_LABELS("graphql/query/getProjectLabels.graphql"),
  @SinceGitLab("15.2")
  GET_PROJECT_WORK_ITEMS("graphql/query/getProjectWidgets.graphql"),
  @SinceGitLab("13.0")
  GET_MEMBER_PROJECTS_FOR_SNIPPETS("graphql/query/getMemberProjectsForSnippets.graphql"),
  @SinceGitLab("13.0")
  GET_MEMBER_PROJECTS_FOR_CLONE("graphql/query/getMemberProjectsForClone.graphql"),

  @SinceGitLab("13.1", note = "Different ID type until 13.6, should work")
  TOGGLE_MERGE_REQUEST_DISCUSSION_RESOLVE("graphql/query/toggleMergeRequestDiscussionResolve.graphql"),

  @SinceGitLab("12.1", note = "Different ID type until 13.6, should work")
  CREATE_NOTE("graphql/query/createNote.graphql"),
  @SinceGitLab("12.1", note = "Different ID type until 13.6, should work")
  CREATE_DIFF_NOTE("graphql/query/createDiffNote.graphql"),
  @SinceGitLab("12.1", note = "Different ID type until 13.6, should work")
  CREATE_REPLY_NOTE("graphql/query/createReplyNote.graphql"),
  @SinceGitLab("13.3.1")
  CREATE_SNIPPET("graphql/query/createSnippet.graphql"),
  @SinceGitLab("12.1", note = "Different ID type until 13.6, should work")
  UPDATE_NOTE("graphql/query/updateNote.graphql"),
  @SinceGitLab("13.3.1", note = "Different ID type until 13.6, should work")
  UPDATE_SNIPPET_BLOB("graphql/query/updateSnippetBlob.graphql"),
  @SinceGitLab("12.1", note = "Different ID type until 13.6, should work")
  DESTROY_NOTE("graphql/query/destroyNote.graphql"),

  @SinceGitLab("13.10")
  MERGE_REQUEST_ACCEPT("graphql/query/mergeRequestAccept.graphql"),
  @SinceGitLab("13.1")
  MERGE_REQUEST_CREATE("graphql/query/mergeRequestCreate.graphql"),
  @SinceGitLab("13.12")
  MERGE_REQUEST_SET_DRAFT("graphql/query/mergeRequestSetDraft.graphql"),
  @SinceGitLab("15.3")
  MERGE_REQUEST_SET_REVIEWERS("graphql/query/mergeRequestSetReviewers.graphql"),
  @SinceGitLab("13.9")
  MERGE_REQUEST_UPDATE("graphql/query/mergeRequestUpdate.graphql"),
  @SinceGitLab("13.9")
  MERGE_REQUEST_REVIEWER_REREVIEW("graphql/query/mergeRequestReviewerRereview.graphql"),

  @SinceGitLab("13.2")
  AWARD_EMOJI_TOGGLE("graphql/query/awardEmojiToggle.graphql")
}
