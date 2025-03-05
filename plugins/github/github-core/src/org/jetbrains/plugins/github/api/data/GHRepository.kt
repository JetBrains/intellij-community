// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data

import com.fasterxml.jackson.annotation.JsonIgnore
import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.data.pullrequest.GHGitRefName

@GraphQLFragment("/graphql/fragment/repository.graphql")
class GHRepository(id: String,
                   val owner: GHRepositoryOwnerName,
                   val nameWithOwner: @NlsSafe String,
                   val viewerPermission: GHRepositoryPermissionLevel?,
                   val mergeCommitAllowed: Boolean,
                   val squashMergeAllowed: Boolean,
                   val rebaseMergeAllowed: Boolean,
                   val defaultBranchRef: GHGitRefName?,
                   val isFork: Boolean,
                   val url: @NlsSafe String,
                   val sshUrl: @NlsSafe String)
  : GHNode(id) {
  @JsonIgnore
  val path: GHRepositoryPath
  @JsonIgnore
  val defaultBranch = defaultBranchRef?.name

  init {
    val split = nameWithOwner.split('/')
    path = GHRepositoryPath(split[0], split[1])
  }
}