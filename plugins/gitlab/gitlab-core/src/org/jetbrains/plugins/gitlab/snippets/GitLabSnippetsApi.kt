// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.snippets

import com.intellij.collaboration.api.data.asParameters
import com.intellij.collaboration.api.graphql.loadResponse
import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.async.collectBatches
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.data.GitLabVisibilityLevel
import org.jetbrains.plugins.gitlab.api.dto.GitLabGraphQLMutationResultDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabProjectsForSnippetsDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabSnippetBlobAction
import org.jetbrains.plugins.gitlab.api.dto.GitLabSnippetDTO
import org.jetbrains.plugins.gitlab.util.GitLabProjectPath
import java.net.http.HttpResponse

private class CreateSnippetResult(snippet: GitLabSnippetDTO?, errors: List<String>)
  : GitLabGraphQLMutationResultDTO<GitLabSnippetDTO>(errors) {
  override val value: GitLabSnippetDTO? = snippet
}

/**
 * Provides a flow for underlying queries to the GitLab GQL API that lookup the projects
 * the user is a member of and can create snippets on.
 */
@SinceGitLab("13.0")
internal fun GitLabApi.GraphQL.getSnippetAllowedProjects(): Flow<List<GitLabProjectCoordinates>> =
  ApiPageUtil.createGQLPagesFlow { page ->
    val parameters = page.asParameters()
    val request = gitLabQuery(GitLabGQLQuery.GET_MEMBER_PROJECTS_FOR_SNIPPETS, parameters)
    withErrorStats(GitLabGQLQuery.GET_MEMBER_PROJECTS_FOR_SNIPPETS) {
      loadResponse<GitLabProjectsForSnippetsDTO>(request, "projects").body()
    }
  }.map {
    it.nodes
      .filter { project -> project.userPermissions.createSnippet }
      .map { project ->
        val projectPath = GitLabProjectPath(project.ownerPath, project.name)
        GitLabProjectCoordinates(server, projectPath)
      }
  }.collectBatches()

/**
 * Sends GQL request to GitLab to create a snippet optionally under the given project,
 * with the given title and description, visibility level, and files and file contents.
 * A file name selection function can be passed to decide how to turn a [VirtualFile]
 * into a file name.
 */
internal suspend fun GitLabApi.GraphQL.createSnippet(
  project: GitLabProjectCoordinates?,
  title: String,
  description: String?,
  visibilityLevel: GitLabVisibilityLevel,
  snippetBlobActions: List<GitLabSnippetBlobAction>
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabSnippetDTO>?> {
  val parameters = mapOf(
    "title" to title,
    "description" to description,
    "visibilityLevel" to visibilityLevel,
    "projectPath" to project?.projectPath?.fullPath(),
    "blobActions" to snippetBlobActions
  )
  val request = gitLabQuery(GitLabGQLQuery.CREATE_SNIPPET, parameters)
  return withErrorStats(GitLabGQLQuery.CREATE_SNIPPET) {
    loadResponse<CreateSnippetResult>(request, "createSnippet")
  }
}
