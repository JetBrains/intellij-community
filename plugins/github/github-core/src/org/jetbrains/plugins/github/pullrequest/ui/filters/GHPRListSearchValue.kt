// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.filters

import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchValue
import com.intellij.openapi.util.text.StringUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery.QualifierName
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery.Term
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery.Term.Qualifier
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery.Term.QueryPart

@ApiStatus.Experimental
@Serializable
data class GHPRListSearchValue(override val searchQuery: String? = null,
                               val state: State? = null,
                               val assignee: String? = null,
                               val reviewState: ReviewState? = null,
                               val author: String? = null,
                               val label: String? = null,
                               val sort: Sort? = null) : ReviewListSearchValue {

  @Transient
  override val filterCount: Int = calcFilterCount()

  private fun calcFilterCount(): Int {
    var count = 0
    if (searchQuery != null) count++
    if (state != null) count++
    if (assignee != null) count++
    if (reviewState != null) count++
    if (author != null) count++
    if (label != null) count++
    if (sort != null) count++
    return count
  }

  fun toQuery(): GHPRSearchQuery? {
    val terms = mutableListOf<Term<*>>()

    if (searchQuery != null) {
      terms.add(QueryPart(searchQuery))
    }

    if (state != null) {
      val term = when (state) {
        State.OPEN -> QualifierName.`is`.createTerm("open")
        State.CLOSED -> QualifierName.`is`.createTerm("closed")
        State.MERGED -> QualifierName.`is`.createTerm("merged")
      }
      terms.add(term)
    }

    if (assignee != null) {
      terms.add(QualifierName.assignee.createTerm(assignee))
    }

    if (reviewState != null) {
      val term = when (reviewState) {
        ReviewState.NO_REVIEW -> QualifierName.review.createTerm("none")
        ReviewState.REQUIRED -> QualifierName.review.createTerm("required")
        ReviewState.APPROVED -> QualifierName.review.createTerm("approved")
        ReviewState.CHANGES_REQUESTED -> QualifierName.review.createTerm("changes-requested")
        ReviewState.REVIEWED_BY_ME -> QualifierName.reviewedBy.createTerm("@me")
        ReviewState.NOT_REVIEWED_BY_ME -> (QualifierName.reviewedBy.createTerm("@me") as Qualifier.Simple).not()
        ReviewState.AWAITING_REVIEW -> QualifierName.reviewRequested.createTerm("@me")
      }
      terms.add(term)
    }

    if (author != null) {
      terms.add(QualifierName.author.createTerm(author))
    }

    if (label != null) {
      terms.add(QualifierName.label.createTerm(StringUtil.wrapWithDoubleQuote(label)))
    }

    if (sort != null) {
      val term = when (sort) {
        Sort.NEWEST -> QualifierName.sortBy.createTerm("created-desc")
        Sort.OLDEST -> QualifierName.sortBy.createTerm("created-asc")
        Sort.MOST_COMMENTED -> QualifierName.sortBy.createTerm("comments-desc")
        Sort.LEAST_COMMENTED -> QualifierName.sortBy.createTerm("comments-asc")
        Sort.RECENTLY_UPDATED -> QualifierName.sortBy.createTerm("updated-desc")
        Sort.LEAST_RECENTLY_UPDATED -> QualifierName.sortBy.createTerm("updated-asc")
      }
      terms.add(term)
    }

    if (terms.isEmpty()) return null
    return GHPRSearchQuery(terms)
  }

  companion object {
    val DEFAULT = GHPRListSearchValue(state = State.OPEN)
    val EMPTY = GHPRListSearchValue()
  }

  enum class State {
    OPEN,
    CLOSED,
    MERGED
  }

  enum class ReviewState {
    NO_REVIEW,
    REQUIRED,
    APPROVED,
    CHANGES_REQUESTED,
    REVIEWED_BY_ME,
    NOT_REVIEWED_BY_ME,
    AWAITING_REVIEW
  }

  enum class Sort {
    NEWEST,
    OLDEST,
    MOST_COMMENTED,
    LEAST_COMMENTED,
    RECENTLY_UPDATED,
    LEAST_RECENTLY_UPDATED,
  }
}
