// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.filters

import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchValue
import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class GitLabMergeRequestsFiltersValue(
  override val searchQuery: String? = null,
  val state: MergeRequestStateFilterValue? = null,
  val author: MergeRequestsMemberFilterValue? = null,
  val assignee: MergeRequestsMemberFilterValue? = null,
  val reviewer: MergeRequestsMemberFilterValue? = null,
  val label: LabelFilterValue? = null,
) : ReviewListSearchValue {
  private val filters: List<FilterValue?> = listOf(state, author, assignee, reviewer, label)

  @Transient
  override val filterCount: Int = calcFilterCount()

  fun toSearchQuery(): String = filters.mapNotNull { it }.joinToString(separator = "&") { filter ->
    "${filter.queryField()}=${filter.queryValue()}"
  }

  private fun calcFilterCount(): Int {
    var count = 0
    if (searchQuery != null) count++
    if (state != null) count++
    if (author != null) count++
    if (assignee != null) count++
    if (reviewer != null) count++
    if (label != null) count++
    return count
  }

  private interface FilterValue {
    fun queryField(): String
    fun queryValue(): String
  }

  @Serializable
  enum class MergeRequestStateFilterValue : FilterValue {
    OPENED, MERGED, CLOSED;

    override fun queryField(): String = "state"

    override fun queryValue(): String = when (this) {
      OPENED -> "opened"
      CLOSED -> "closed"
      MERGED -> "merged"
    }
  }

  @Serializable
  sealed class MergeRequestsMemberFilterValue(val username: @NlsSafe String, val fullname: @NlsSafe String) : FilterValue {
    override fun queryValue(): String = username
  }

  internal class MergeRequestsAuthorFilterValue(username: String, fullname: String)
    : MergeRequestsMemberFilterValue(username, fullname) {
    override fun queryField(): String = "author_username"
  }

  internal class MergeRequestsAssigneeFilterValue(username: String, fullname: String)
    : MergeRequestsMemberFilterValue(username, fullname) {
    override fun queryField(): String = "assignee_username"
  }

  internal class MergeRequestsReviewerFilterValue(username: String, fullname: String)
    : MergeRequestsMemberFilterValue(username, fullname) {
    override fun queryField(): String = "reviewer_username"
  }

  @Serializable
  class LabelFilterValue(val title: String) : FilterValue {
    override fun queryField(): String = "labels"
    override fun queryValue(): String = title
  }

  companion object {
    val EMPTY = GitLabMergeRequestsFiltersValue()
    val DEFAULT = GitLabMergeRequestsFiltersValue(state = MergeRequestStateFilterValue.OPENED)
  }
}