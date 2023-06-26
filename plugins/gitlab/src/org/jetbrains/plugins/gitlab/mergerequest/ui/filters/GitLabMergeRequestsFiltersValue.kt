// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.filters

import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchValue
import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.net.URLEncoder

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

  fun toSearchQuery(): String = filters.asSequence()
    .filterNotNull()
    .map { "${it.queryField()}=${URLEncoder.encode(it.queryValue(), Charsets.UTF_8)}" }
    .let { if (searchQuery != null) it + "search=${URLEncoder.encode(searchQuery, Charsets.UTF_8)}" else it }
    .joinToString(separator = "&")

  private fun calcFilterCount(): Int {
    var count = 0
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
  sealed class MergeRequestsMemberFilterValue : FilterValue {
    abstract val username: @NlsSafe String
    abstract val fullname: @NlsSafe String

    override fun queryValue(): String = username

    @Serializable
    internal class MergeRequestsAuthorFilterValue(
      override val username: @NlsSafe String,
      override val fullname: @NlsSafe String
    ) : MergeRequestsMemberFilterValue() {
      override fun queryField(): String = "author_username"
    }

    @Serializable
    internal class MergeRequestsAssigneeFilterValue(
      override val username: @NlsSafe String,
      override val fullname: @NlsSafe String
    ) : MergeRequestsMemberFilterValue() {
      override fun queryField(): String = "assignee_username"
    }

    @Serializable
    internal class MergeRequestsReviewerFilterValue(
      override val username: @NlsSafe String,
      override val fullname: @NlsSafe String
    ) : MergeRequestsMemberFilterValue() {
      override fun queryField(): String = "reviewer_username"
    }
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