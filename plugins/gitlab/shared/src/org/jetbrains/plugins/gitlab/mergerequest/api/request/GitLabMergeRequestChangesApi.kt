// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.json.loadJsonList
import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.api.page.foldToList
import com.intellij.collaboration.util.resolveRelative
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitDetailedRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiffDTO
import org.jetbrains.plugins.gitlab.api.getMetadataOrNull
import org.jetbrains.plugins.gitlab.api.loadValue
import org.jetbrains.plugins.gitlab.api.projectApiUrl
import org.jetbrains.plugins.gitlab.api.withErrorStats
import org.jetbrains.plugins.gitlab.api.withQuery
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName

@SinceGitLab("9.0", note = "Not an exact version")
suspend fun GitLabApi.Rest.loadMergeRequestCommits(projectId: String, mrIid: String): List<GitLabCommitRestDTO> {
  val uri = projectApiUrl(projectId)
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("commits")
  return ApiPageUtil.createPagesFlowByLinkHeader(uri) { pageUri ->
    withErrorStats(GitLabApiRequestName.REST_GET_MERGE_REQUEST_COMMITS) {
      request(pageUri).GET().build()
        .loadJsonList<GitLabCommitRestDTO>()
    }
  }.map { it.body() }.foldToList()
}


data class GitLabChangesHolderDTO(
  val changes: List<GitLabDiffDTO>,
)

@SinceGitLab("9.0", deprecatedIn = "15.7", note = "Deprecated in favour of /diffs")
suspend fun GitLabApi.Rest.loadMergeRequestChanges(projectId: String, mrIid: String): List<GitLabDiffDTO> {
  requireNotNull(getMetadataOrNull())
  val uri = projectApiUrl(projectId)
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("changes")
  return ApiPageUtil.createPagesFlowByLinkHeader(uri) { pageUri ->
    withErrorStats(GitLabApiRequestName.REST_GET_MERGE_REQUEST_CHANGES) {
      request(pageUri).GET().build()
        .loadJsonValue<GitLabChangesHolderDTO>()
    }
  }.map { it.body().changes }.foldToList()
}

@SinceGitLab("15.7")
suspend fun GitLabApi.Rest.loadMergeRequestDiffs(projectId: String, mrIid: String): List<GitLabDiffDTO> {
  requireNotNull(getMetadataOrNull())
  // doesn't send back Link headers, so paginate by page number
  return ApiPageUtil.createPagesFlowByPagination { page ->
    val uri = projectApiUrl(projectId)
      .resolveRelative("merge_requests")
      .resolveRelative(mrIid)
      .resolveRelative("diffs")
      .withQuery {
        "page" eq page
      }
    withErrorStats(GitLabApiRequestName.REST_GET_MERGE_REQUEST_DIFF) {
      request(uri).GET().build()
        .loadJsonList<GitLabDiffDTO>()
    }
  }.map { it.body() }.foldToList()
}

@SinceGitLab("7.0")
suspend fun GitLabApi.Rest.loadCommitDiffs(projectId: String, commitSha: String): List<GitLabDiffDTO> {
  val uri = projectApiUrl(projectId)
    .resolveRelative("repository")
    .resolveRelative("commits")
    .resolveRelative(commitSha)
    .resolveRelative("diff")
  return ApiPageUtil.createPagesFlowByLinkHeader(uri) { pageUri ->
    withErrorStats(GitLabApiRequestName.REST_GET_COMMIT_DIFF) {
      request(pageUri).GET().build()
        .loadJsonList<GitLabDiffDTO>()
    }
  }.map { it.body() }.foldToList()
}

@SinceGitLab("7.0")
suspend fun GitLabApi.Rest.loadCommit(
  projectId: String,
  commitSha: String,
): GitLabCommitDetailedRestDTO {
  val uri = projectApiUrl(projectId)
    .resolveRelative("repository")
    .resolveRelative("commits")
    .resolveRelative(commitSha)
  return request(uri).GET().build()
    .loadValue(GitLabApiRequestName.REST_GET_COMMIT)
}
