// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.filters

import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchValue
import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable

@Serializable
data class GitLabMergeRequestsFiltersValue(
  override val searchQuery: String? = null,
  val state: MergeRequestStateFilterValue? = null,
  val author: MergeRequestsMemberFilterValue? = null,
) : ReviewListSearchValue {
  private val filters: List<FilterValue?> = listOf(state, author)

  fun toSearchQuery(): String = filters.mapNotNull { it }.joinToString(separator = "&") { filter ->
    "${filter.queryField()}=${filter.queryValue()}"
  }

  private interface FilterValue {
    fun queryField(): String
    fun queryValue(): String
  }

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

  companion object {
    val EMPTY = GitLabMergeRequestsFiltersValue()
    val DEFAULT = GitLabMergeRequestsFiltersValue(state = MergeRequestStateFilterValue.OPENED)
  }
}