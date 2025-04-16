// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.json.loadJsonList
import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.util.resolveRelative
import com.intellij.collaboration.util.withQuery
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitDetailedRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiffDTO
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.net.URI
import java.net.http.HttpResponse

@SinceGitLab("9.0", note = "Not an exact version")
suspend fun GitLabApi.getMergeRequestCommitsURI(project: GitLabProjectCoordinates, mrIid: String): URI {
  return project.restApiUri
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("commits")
}

@SinceGitLab("9.0", note = "Not an exact version")
suspend fun GitLabApi.Rest.loadMergeRequestCommits(uri: URI): HttpResponse<out List<GitLabCommitRestDTO>> {
  val request = request(uri).GET().build()
  return withErrorStats(GitLabApiRequestName.REST_GET_MERGE_REQUEST_COMMITS) {
    loadJsonList<GitLabCommitRestDTO>(request)
  }
}


data class GitLabChangesHolderDTO(
  val changes: List<GitLabDiffDTO>,
)

@SinceGitLab("9.0", deprecatedIn = "15.7", note = "Deprecated in favour of /diffs")
suspend fun GitLabApi.Rest.loadMergeRequestChanges(uri: URI): HttpResponse<out GitLabChangesHolderDTO> {
  val request = request(uri).GET().build()
  return withErrorStats(GitLabApiRequestName.REST_GET_MERGE_REQUEST_CHANGES) {
    loadJsonValue<GitLabChangesHolderDTO>(request)
  }
}

@SinceGitLab("9.0", deprecatedIn = "15.7", note = "Deprecated in favour of /diffs")
suspend fun GitLabApi.getMergeRequestChangesURI(project: GitLabProjectCoordinates, mrIid: String): URI {
  val metadata = getMetadataOrNull()
  requireNotNull(metadata)

  return project.restApiUri
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("changes")
}

@SinceGitLab("15.7")
suspend fun GitLabApi.Rest.loadMergeRequestDiffs(uri: URI): HttpResponse<out List<GitLabDiffDTO>> {
  val request = request(uri).GET().build()
  return withErrorStats(GitLabApiRequestName.REST_GET_MERGE_REQUEST_DIFF) {
    loadJsonList<GitLabDiffDTO>(request)
  }
}

@SinceGitLab("15.7")
suspend fun GitLabApi.getMergeRequestDiffsURI(project: GitLabProjectCoordinates, mrIid: String, page: Int): URI {
  val metadata = getMetadataOrNull()
  requireNotNull(metadata)

  return project.restApiUri
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("diffs")
    .withQuery("page=$page")
}

@SinceGitLab("7.0")
suspend fun GitLabApi.Rest.loadCommitDiffs(uri: URI): HttpResponse<out List<GitLabDiffDTO>> {
  val request = request(uri).GET().build()
  return withErrorStats(GitLabApiRequestName.REST_GET_COMMIT_DIFF) {
    loadJsonList(request)
  }
}

@SinceGitLab("7.0")
fun getCommitDiffsURI(project: GitLabProjectCoordinates, commitSha: String): URI =
  project.restApiUri
    .resolveRelative("repository")
    .resolveRelative("commits")
    .resolveRelative(commitSha)
    .resolveRelative("diff")

@SinceGitLab("7.0")
suspend fun GitLabApi.Rest.loadCommit(
  project: GitLabProjectCoordinates,
  commitSha: String,
): HttpResponse<out GitLabCommitDetailedRestDTO> {
  val uri = project.restApiUri
    .resolveRelative("repository")
    .resolveRelative("commits")
    .resolveRelative(commitSha)
  val request = request(uri).GET().build()
  return withErrorStats(GitLabApiRequestName.REST_GET_COMMIT) {
    loadJsonValue(request)
  }
}

