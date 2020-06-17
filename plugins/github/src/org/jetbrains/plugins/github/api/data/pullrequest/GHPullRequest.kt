// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
                    viewerCanUpdate: Boolean,
                    viewerDidAuthor: Boolean,
                    val bodyHTML: String,
                    @JsonProperty("reviewRequests") reviewRequests: GHNodes<GHPullRequestReviewRequest>,
                    val baseRefName: String,
                    val baseRefOid: String,
                    val baseRepository: Repository?,
                    val headRefName: String,
                    val headRefOid: String,
                    val headRepository: HeadRepository?)
  : GHPullRequestShort(id, url, number, title, state, author, createdAt, assignees, labels, viewerCanUpdate, viewerDidAuthor) {

  @JsonIgnore
  val reviewRequests = reviewRequests.nodes

  open class Repository(val owner: Owner, val isFork: Boolean)

  class HeadRepository(owner: Owner, isFork: Boolean, val url: String, val sshUrl: String) : Repository(owner, isFork)

  class Owner(val login: String)
}
