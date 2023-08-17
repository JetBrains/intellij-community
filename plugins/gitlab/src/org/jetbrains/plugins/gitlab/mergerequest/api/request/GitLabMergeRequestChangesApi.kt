// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.json.loadJsonList
import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiffDTO
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.net.URI
import java.net.http.HttpResponse

@SinceGitLab("15.7", note = "Older version is merge_requests/:iid/changes")
suspend fun GitLabApi.Rest.loadMergeRequestDiffs(serverPath: GitLabServerPath, uri: URI): HttpResponse<out List<GitLabDiffDTO>> {
  val request = request(uri).GET().build()
  return withErrorStats(serverPath, GitLabApiRequestName.REST_GET_MERGE_REQUEST_DIFF) {
    loadJsonList(request)
  }
}

@SinceGitLab("15.7", note = "Older version is merge_requests/:iid/changes")
fun getMergeRequestDiffsURI(project: GitLabProjectCoordinates, mrIid: String): URI =
  project.restApiUri
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("diffs")

@SinceGitLab("7.0")
suspend fun GitLabApi.Rest.loadCommitDiffs(serverPath: GitLabServerPath, uri: URI): HttpResponse<out List<GitLabDiffDTO>> {
  val request = request(uri).GET().build()
  return withErrorStats(serverPath, GitLabApiRequestName.REST_GET_COMMIT_DIFF) {
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
suspend fun GitLabApi.Rest.loadCommit(project: GitLabProjectCoordinates,
                                      commitSha: String): HttpResponse<out GitLabCommitRestDTO> {
  val uri = project.restApiUri
    .resolveRelative("repository")
    .resolveRelative("commits")
    .resolveRelative(commitSha)
  val request = request(uri).GET().build()
  return withErrorStats(project.serverPath, GitLabApiRequestName.REST_GET_COMMIT) {
    loadJsonValue(request)
  }
}

