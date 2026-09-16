// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.request

import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.api.dto.GitLabGroupRestDTO
import org.jetbrains.plugins.gitlab.api.loadList
import org.jetbrains.plugins.gitlab.api.withQuery
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName

/**
 * Returns up to 20 groups that match the search string.
 * For s search string of fewer than 3 characters, the endpoint returns nothing, so let's not use a search parameter then.
 */
@SinceGitLab("14.0", note = "No exact version")
suspend fun GitLabApi.Rest.searchGroups(searchString: String): List<GitLabGroupRestDTO> {
  val uri = server.restApiUri
    .resolveRelative("groups")
    .withQuery {
      "search" eq searchString
    }

  return request(uri).GET().build()
    .loadList(GitLabApiRequestName.REST_GET_GROUPS)
}

/**
 * Returns up to 20 groups.
 */
@SinceGitLab("14.0", note = "No exact version")
suspend fun GitLabApi.Rest.searchGroups(): List<GitLabGroupRestDTO> {
  val uri = server.restApiUri
    .resolveRelative("groups")

  return request(uri).GET().build()
    .loadList(GitLabApiRequestName.REST_GET_GROUPS)
}
