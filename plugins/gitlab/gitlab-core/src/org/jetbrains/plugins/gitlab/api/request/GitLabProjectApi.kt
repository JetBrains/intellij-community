// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.request

import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.collaboration.api.data.asParameters
import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.graphql.loadResponse
import com.intellij.collaboration.api.json.loadJsonList
import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.util.resolveRelative
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.*
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.net.URI
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse

@SinceGitLab("12.0")
suspend fun GitLabApi.GraphQL.findProject(project: GitLabProjectCoordinates): HttpResponse<out GitLabProjectDTO?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
  )
  val request = gitLabQuery(GitLabGQLQuery.GET_PROJECT, parameters)
  return withErrorStats(GitLabGQLQuery.GET_PROJECT) {
    loadResponse<GitLabProjectDTO>(request, "project")
  }
}

suspend fun GitLabApi.Rest.createProject(
  namespaceId: GitLabId?, name: String,
  isPrivate: Boolean, description: String,
): HttpResponse<out GitLabRepositoryCreationRestDTO> {
  val uri = server.restApiUri.resolveRelative("projects")
    .withParams(listOfNotNull(
      namespaceId?.guessRestId()?.let { "namespace_id" to it },
      "name" to name,
      "visibility" to if (isPrivate) "private" else "public",
      "description" to description,
    ).toMap())
  val request = request(uri).POST(BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_CREATE_PROJECT) {
    loadJsonValue<GitLabRepositoryCreationRestDTO>(request)
  }
}

@SinceGitLab("16.9")
private suspend fun GitLabApi.GraphQL.isProjectForked(project: GitLabProjectCoordinates): HttpResponse<out Boolean> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
  )
  val request = gitLabQuery(GitLabGQLQuery.GET_PROJECT_IS_FORKED, parameters)
  return withErrorStats(GitLabGQLQuery.GET_PROJECT_IS_FORKED) {
    loadResponse<Boolean>(request, "project", "isForked")
  }
}

@SinceGitLab("12.0")
private suspend fun GitLabApi.Rest.isProjectForked(project: GitLabProjectCoordinates): HttpResponse<out GitLabProjectIsForkedDTO> {
  val uri = project.restApiUri
  val request = request(uri).GET().build()
  return withErrorStats(GitLabApiRequestName.REST_GET_PROJECT_IS_FORKED) {
    loadJsonValue(request)
  }
}

suspend fun GitLabApi.isProjectForked(project: GitLabProjectCoordinates): Boolean =
  if (getMetadata().version < GitLabVersion(16, 9)) {
    rest.isProjectForked(project).body().isForked
  }
  else {
    graphQL.isProjectForked(project).body()
  }


@SinceGitLab("13.1", note = "No exact version")
fun GitLabApi.GraphQL.createAllProjectLabelsFlow(project: GitLabProjectCoordinates): Flow<List<GitLabLabelDTO>> =
  ApiPageUtil.createGQLPagesFlow { page ->
    val parameters = page.asParameters() + mapOf(
      "fullPath" to project.projectPath.fullPath()
    )
    val request = gitLabQuery(GitLabGQLQuery.GET_PROJECT_LABELS, parameters)
    withErrorStats(GitLabGQLQuery.GET_PROJECT_LABELS) {
      loadResponse<LabelConnection>(request, "project", "labels").body()
    }
  }.map { it.nodes }

@SinceGitLab("15.2")
fun GitLabApi.GraphQL.createAllWorkItemsFlow(project: GitLabProjectCoordinates): Flow<List<GitLabWorkItemDTO>> =
  ApiPageUtil.createGQLPagesFlow { page ->
    val parameters = page.asParameters() + mapOf(
      "fullPath" to project.projectPath.fullPath()
    )
    val request = gitLabQuery(GitLabGQLQuery.GET_PROJECT_WORK_ITEMS, parameters)
    withErrorStats(GitLabGQLQuery.GET_PROJECT_WORK_ITEMS) {
      loadResponse<WorkItemConnection>(request, "project", "workItems").body()
    }
  }.map { it.nodes }

@SinceGitLab("7.0", note = "No exact version")
fun getProjectUsersURI(project: GitLabProjectCoordinates) = project.restApiUri.resolveRelative("users")

@SinceGitLab("7.0", note = "No exact version")
suspend fun GitLabApi.Rest.getProjectUsers(uri: URI): HttpResponse<out List<GitLabUserRestDTO>> {
  val request = request(uri).GET().build()
  return withErrorStats(GitLabApiRequestName.REST_GET_PROJECT_USERS) {
    loadJsonList(request)
  }
}

@ApiStatus.Internal
@SinceGitLab("13.0")
fun GitLabApi.GraphQL.getCloneableProjects(): Flow<List<GitLabProjectForCloneDTO>> =
  ApiPageUtil.createGQLPagesFlow { page ->
    val parameters = page.asParameters()
    val request = gitLabQuery(GitLabGQLQuery.GET_MEMBER_PROJECTS_FOR_CLONE, parameters)
    withErrorStats(GitLabGQLQuery.GET_MEMBER_PROJECTS_FOR_CLONE) {
      loadResponse<GitLabProjectsForCloneDTO>(request, "projects").body()
    }
  }.map { it.nodes }

@SinceGitLab("10.3")
suspend fun GitLabApi.Rest.getProjectNamespace(namespaceId: String): HttpResponse<out GitLabNamespaceRestDTO> {
  val uri = server.restApiUri.resolveRelative("namespaces").resolveRelative(namespaceId)
  val request = request(uri).GET().build()
  return withErrorStats(GitLabApiRequestName.REST_GET_PROJECT_NAMESPACE) {
    loadJsonValue(request)
  }
}

@ApiStatus.Internal
@SinceGitLab("14.3", note = "Doesn't fetch subgroups before 17.10")
fun GitLabApi.GraphQL.getMemberNamespacesForShare(glMetadata: GitLabServerMetadata): Flow<List<WithGitLabNamespace>> =
  ApiPageUtil.createGQLPagesFlow { initialPage ->
    val page = GraphQLRequestPagination(initialPage.afterCursor, 10)

    val parameters = page.asParameters()

    val result = if (glMetadata.version < GitLabVersion(17, 10)) {
      val request = gitLabQuery(GitLabGQLQuery.GET_MEMBER_NAMESPACES_OLD, parameters)
      withErrorStats(GitLabGQLQuery.GET_MEMBER_NAMESPACES_OLD) {
        loadResponse<GitLabUserNamespacesResult>(request)
      }
    } else {
      val request = gitLabQuery(GitLabGQLQuery.GET_MEMBER_NAMESPACES, parameters)
      withErrorStats(GitLabGQLQuery.GET_MEMBER_NAMESPACES) {
        loadResponse<GitLabUserNamespacesResult>(request)
      }
    }.body()

    val namespaces = (if (page.afterCursor == null) listOf(result.currentUser.namespace) else listOf()) +
                     result.groups.nodes.filter { it.userPermissions.createProjects }
    GraphQLConnectionDTO<WithGitLabNamespace>(result.groups.pageInfo, namespaces)
  }.map { it.nodes }

private data class GitLabUserNamespacesResult(
  val currentUser: CurrentUser,
  val groups: GraphQLConnectionDTO<GitLabGroupDTO>,
) {
  data class CurrentUser(val namespace: GitLabNamespaceDTO)
}

@SinceGitLab("13.1")
suspend fun GitLabApi.GraphQL.createMergeRequest(
  project: GitLabProjectCoordinates,
  sourceBranch: String,
  targetBranch: String,
  title: String,
  description: String?,
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>?> {
  val parameters = mapOf(
    "projectId" to project.projectPath.fullPath(),
    "sourceBranch" to sourceBranch,
    "targetBranch" to targetBranch,
    "title" to title,
    "description" to description
  )

  val request = gitLabQuery(GitLabGQLQuery.MERGE_REQUEST_CREATE, parameters)
  return withErrorStats(GitLabGQLQuery.MERGE_REQUEST_CREATE) {
    loadResponse<GitLabCreateMergeRequestResult>(request, "mergeRequestCreate")
  }
}

private class LabelConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabLabelDTO>)
  : GraphQLConnectionDTO<GitLabLabelDTO>(pageInfo, nodes)

private class WorkItemConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabWorkItemDTO>)
  : GraphQLConnectionDTO<GitLabWorkItemDTO>(pageInfo, nodes)

private class GitLabCreateMergeRequestResult(
  mergeRequest: GitLabMergeRequestDTO,
  errors: List<String>?,
  override val value: GitLabMergeRequestDTO = mergeRequest,
) : GitLabGraphQLMutationResultDTO<GitLabMergeRequestDTO>(errors)