// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.filters

import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchValue
import kotlinx.serialization.Serializable

@Serializable
data class GitLabMergeRequestsFiltersValue(
  override val searchQuery: String? = null,
  val state: MergeRequestStateFilterValue? = null
) : ReviewListSearchValue {
  private val filters: List<FilterValue?> = listOf(state)

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

  companion object {
    val EMPTY = GitLabMergeRequestsFiltersValue()
  }
}