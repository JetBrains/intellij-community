// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.collaboration.api.dto.GraphQLNodesDTO
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHReactable
import org.jetbrains.plugins.github.api.data.GHRepository
import org.jetbrains.plugins.github.api.data.GHUser
import java.util.Date

@GraphQLFragment("/graphql/fragment/pullRequestInfo.graphql")
class GHPullRequest(
  id: String,
  url: String,
  number: Long,
  title: String,
  state: GHPullRequestState,
  isDraft: Boolean,
  author: GHActor?,
  createdAt: Date,
  updatedAt: Date,
  @JsonProperty("assignees") assignees: GraphQLNodesDTO<GHUser>,
  @JsonProperty("labels") labels: GraphQLNodesDTO<GHLabel>?,
  @JsonProperty("reviewRequests") reviewRequests: GraphQLNodesDTO<GHPullRequestReviewRequest>?,
  @JsonProperty("reviewThreads") reviewThreads: GraphQLNodesDTO<ReviewThreadDetails>,
  @JsonProperty("reviews") reviews: GraphQLNodesDTO<GHPullRequestReview>?,
  val reviewDecision: GHPullRequestReviewDecision?,
  mergeable: GHPullRequestMergeableState,
  viewerCanUpdate: Boolean,
  viewerCanReact: Boolean,
  viewerDidAuthor: Boolean,
  val viewerCanDeleteHeadRef: Boolean,
  val body: @NlsSafe String,
  val baseRefName: String,
  val baseRefOid: String,
  val baseRepository: GHRepository?,
  private val headRef: HeadRef?,
  val headRefName: String,
  val headRefOid: String,
  val headRepository: GHRepository?,
  override val reactions: GHReactable.ReactionConnection,
)
  : GHPullRequestShort(id, url, number, title, state, isDraft, author, createdAt, updatedAt,
                       assignees, labels, reviewRequests, reviewThreads,
                       reviews, mergeable, viewerCanUpdate, viewerCanReact, viewerDidAuthor, reactions) {
  val headRefId: String? get() = headRef?.id

  data class HeadRef(val id: String?)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHPullRequest) return false
    if (!super.equals(other)) return false

    if (viewerCanDeleteHeadRef != other.viewerCanDeleteHeadRef) return false
    if (reviewDecision != other.reviewDecision) return false
    if (body != other.body) return false
    if (baseRefName != other.baseRefName) return false
    if (baseRefOid != other.baseRefOid) return false
    if (baseRepository != other.baseRepository) return false
    if (headRef != other.headRef) return false
    if (headRefName != other.headRefName) return false
    if (headRefOid != other.headRefOid) return false
    if (headRepository != other.headRepository) return false
    if (reviews != other.reviews) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + viewerCanDeleteHeadRef.hashCode()
    result = 31 * result + (reviewDecision?.hashCode() ?: 0)
    result = 31 * result + body.hashCode()
    result = 31 * result + baseRefName.hashCode()
    result = 31 * result + baseRefOid.hashCode()
    result = 31 * result + (baseRepository?.hashCode() ?: 0)
    result = 31 * result + headRef.hashCode()
    result = 31 * result + headRefName.hashCode()
    result = 31 * result + headRefOid.hashCode()
    result = 31 * result + (headRepository?.hashCode() ?: 0)
    result = 31 * result + reviews.hashCode()
    return result
  }
}