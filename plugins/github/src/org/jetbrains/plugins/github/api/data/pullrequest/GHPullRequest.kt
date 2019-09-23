// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHNodes
import org.jetbrains.plugins.github.api.data.GHUser
import java.util.*

class GHPullRequest(id: String,
                    url: String,
                    number: Long,
                    title: String,
                    state: GHPullRequestState,
                    author: GHActor?,
                    createdAt: Date,
                    @JsonProperty("assignees") assignees: GHNodes<GHUser>,
                    @JsonProperty("labels") labels: GHNodes<GHLabel>,
                    val bodyHTML: String,
                    val mergeable: GHPullRequestMergeableState,
                    @JsonProperty("reviewRequests") reviewRequests: GHNodes<GHPullRequestReviewRequest>,
                    val baseRefName: String,
                    val baseRefOid: String,
                    headRefName: String,
                    val headRefOid: String,
                    headRepository: Repository?,
                    val viewerCanUpdate: Boolean,
                    val viewerDidAuthor: Boolean)
  : GHPullRequestShort(id, url, number, title, state, author, createdAt, assignees, labels) {

  @JsonIgnore
  val reviewRequests = reviewRequests.nodes
  @JsonIgnore
  val headLabel = headRepository?.nameWithOwner.orEmpty() + ":" + headRefName

  class Repository(val nameWithOwner: String)
}
