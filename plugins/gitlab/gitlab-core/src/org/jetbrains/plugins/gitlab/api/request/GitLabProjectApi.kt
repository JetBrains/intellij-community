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
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabGQLQuery
import org.jetbrains.plugins.gitlab.api.GitLabId
import org.jetbrains.plugins.gitlab.api.GitLabServerMetadata
import org.jetbrains.plugins.gitlab.api.GitLabVersion
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.api.dto.GitLabGroupDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabLabelGQLDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNamespaceDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNamespaceRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabProjectDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabProjectForCloneDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabProjectIsForkedDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabProjectRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabProjectsForCloneDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabRepositoryCreationRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabWorkItemDTO
import org.jetbrains.plugins.gitlab.api.dto.WithGitLabNamespace
import org.jetbrains.plugins.gitlab.api.gitLabQuery
import org.jetbrains.plugins.gitlab.api.projectApiUrl
import org.jetbrains.plugins.gitlab.api.withErrorStats
import org.jetbrains.plugins.gitlab.api.withQuery
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import org.jetbrains.plugins.gitlab.util.GitLabProjectPath
import java.net.URI
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse

@SinceGitLab("12.0")
suspend fun GitLabApi.GraphQL.findProject(projectPath: GitLabProjectPath): HttpResponse<out GitLabProjectDTO?> {
  val parameters = mapOf(
    "projectId" to projectPath.fullPath(),
  )
  val request = gitLabQuery(GitLabGQLQuery.GET_PROJECT, parameters)
  return withErrorStats(GitLabGQLQuery.GET_PROJECT) {
    loadResponse<GitLabProjectDTO>(request, "project")
  }
}

suspend fun GitLabApi.Rest.createProject(
  namespaceId: GitLabId?,
  name: String,
  isPrivate: Boolean,
  description: String,
): HttpResponse<out GitLabRepositoryCreationRestDTO> {
  val uri = server.restApiUri.resolveRelative("projects").withQuery {
    "namespace_id" eq namespaceId?.guessRestId()
    "name" eq name
    "visibility" eq if (isPrivate) "private" else "public"
    "description" eq description
  }
  val request = request(uri).POST(BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_CREATE_PROJECT) {
    loadJsonValue<GitLabRepositoryCreationRestDTO>(request)
  }
}

@SinceGitLab("16.9")
private suspend fun GitLabApi.GraphQL.isProjectForked(projectPath: GitLabProjectPath): HttpResponse<out Boolean> {
  val parameters = mapOf(
    "projectId" to projectPath.fullPath(),
  )
  val request = gitLabQuery(GitLabGQLQuery.GET_PROJECT_IS_FORKED, parameters)
  return withErrorStats(GitLabGQLQuery.GET_PROJECT_IS_FORKED) {
    loadResponse<Boolean>(request, "project", "isForked")
  }
}

@SinceGitLab("12.0")
private suspend fun GitLabApi.Rest.isProjectForked(projectPath: GitLabProjectPath): HttpResponse<out GitLabProjectIsForkedDTO> {
  val request = request(projectApiUrl(projectPath.fullPath())).GET().build()
  return withErrorStats(GitLabApiRequestName.REST_GET_PROJECT_IS_FORKED) {
    loadJsonValue(request)
  }
}

@SinceGitLab("3.0", note = "Not an exact version")
suspend fun GitLabApi.Rest.getProject(projectPath: GitLabProjectPath): HttpResponse<out GitLabProjectRestDTO> {
  val request = request(projectApiUrl(projectPath.fullPath())).GET().build()
  return withErrorStats(GitLabApiRequestName.REST_GET_PROJECT) {
    loadJsonValue(request)
  }
}

suspend fun GitLabApi.isProjectForked(projectPath: GitLabProjectPath): Boolean =
  if (getMetadata().version < GitLabVersion(16, 9)) {
    rest.isProjectForked(projectPath).body().isForked
  }
  else {
    graphQL.isProjectForked(projectPath).body()
  }


@SinceGitLab("13.1", note = "No exact version")
fun GitLabApi.GraphQL.createAllProjectLabelsFlow(projectPath: GitLabProjectPath): Flow<List<GitLabLabelGQLDTO>> =
  ApiPageUtil.createGQLPagesFlow { page ->
    val parameters = page.asParameters() + mapOf(
      "fullPath" to projectPath.fullPath()
    )
    val request = gitLabQuery(GitLabGQLQuery.GET_PROJECT_LABELS, parameters)
    withErrorStats(GitLabGQLQuery.GET_PROJECT_LABELS) {
      loadResponse<LabelConnection>(request, "project", "labels").body()
    }
  }.map { it.nodes }

@SinceGitLab("15.2")
fun GitLabApi.GraphQL.createAllWorkItemsFlow(projectPath: GitLabProjectPath): Flow<List<GitLabWorkItemDTO>> =
  ApiPageUtil.createGQLPagesFlow { page ->
    val parameters = page.asParameters() + mapOf(
      "fullPath" to projectPath.fullPath()
    )
    val request = gitLabQuery(GitLabGQLQuery.GET_PROJECT_WORK_ITEMS, parameters)
    withErrorStats(GitLabGQLQuery.GET_PROJECT_WORK_ITEMS) {
      loadResponse<WorkItemConnection>(request, "project", "workItems").body()
    }
  }.map { it.nodes }

@SinceGitLab("7.0", note = "No exact version")
fun GitLabApi.Rest.getProjectUsersURI(projectId: String): URI = projectApiUrl(projectId).resolveRelative("users")

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

private class LabelConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabLabelGQLDTO>)
  : GraphQLConnectionDTO<GitLabLabelGQLDTO>(pageInfo, nodes)

private class WorkItemConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabWorkItemDTO>)
  : GraphQLConnectionDTO<GitLabWorkItemDTO>(pageInfo, nodes)