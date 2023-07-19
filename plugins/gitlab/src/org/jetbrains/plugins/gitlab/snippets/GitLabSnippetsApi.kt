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
import org.jetbrains.plugins.gitlab.api.data.GitLabSnippetBlobAction
import org.jetbrains.plugins.gitlab.api.data.GitLabVisibilityLevel
import org.jetbrains.plugins.gitlab.api.dto.GitLabGraphQLMutationResultDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabProjectsDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabSnippetBlobActionInputType
import org.jetbrains.plugins.gitlab.api.dto.GitLabSnippetDTO
import org.jetbrains.plugins.gitlab.project.GitLabProjectPath
import java.net.http.HttpResponse

private class CreateSnippetResult(snippet: GitLabSnippetDTO, errors: List<String>)
  : GitLabGraphQLMutationResultDTO<GitLabSnippetDTO>(errors) {
  override val value: GitLabSnippetDTO = snippet
}

suspend fun GitLabApi.GraphQL.getAllOwnedProjects(
  serverPath: GitLabServerPath
): Flow<List<GitLabProjectCoordinates>> =
  ApiPageUtil.createGQLPagesFlow { page ->
    val parameters = page.asParameters()
    val request = gitLabQuery(serverPath, GitLabGQLQuery.GET_OWNED_PROJECTS, parameters)
    withErrorStats(serverPath, GitLabGQLQuery.GET_OWNED_PROJECTS) {
      loadResponse<GitLabProjectsDTO>(request, "projects").body()
    }
  }.map {
    it.nodes.map { project ->
      val projectPath = GitLabProjectPath(project.ownerPath, project.name)
      GitLabProjectCoordinates(serverPath, projectPath)
    }
  }.collectBatches()

suspend fun GitLabApi.GraphQL.createSnippet(
  serverPath: GitLabServerPath,
  project: GitLabProjectCoordinates?,
  title: String,
  description: String?,
  visibilityLevel: GitLabVisibilityLevel,
  contents: List<GitLabSnippetFileContents>,
  fileNameSelection: (VirtualFile) -> String,
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabSnippetDTO>?> {
  val parameters = mapOf(
    "title" to title,
    "description" to description,
    "visibilityLevel" to visibilityLevel,
    "projectPath" to project?.projectPath?.fullPath(),
    "blobActions" to contents.map { glContents ->
      GitLabSnippetBlobActionInputType(
        GitLabSnippetBlobAction.create,
        glContents.capturedContents,
        glContents.file?.let(fileNameSelection) ?: "",
        null
      )
    }
  )
  val request = gitLabQuery(serverPath, GitLabGQLQuery.CREATE_SNIPPET, parameters)
  return withErrorStats(serverPath, GitLabGQLQuery.CREATE_SNIPPET) {
    loadResponse<CreateSnippetResult>(request, "createSnippet")
  }
}
