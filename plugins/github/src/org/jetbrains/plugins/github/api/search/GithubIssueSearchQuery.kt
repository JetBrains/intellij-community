// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.search

import org.jetbrains.plugins.github.api.GithubFullPath
import org.jetbrains.plugins.github.api.util.GithubApiSearchTermBuilder

//https://developer.github.com/v3/search/#search-issues
//region parameters
/*
type With this qualifier you can restrict the search to issues (issue) or pull request (pr) only.
in Qualifies which fields are searched. With this qualifier you can restrict the search to just the title (title), body (body), comments (comments), or any combination of these.
author Finds issues or pull requests created by a certain user.
assignee Finds issues or pull requests that are assigned to a certain user.
mentions Finds issues or pull requests that mention a certain user.
commenter Finds issues or pull requests that a certain user commented on.
involves Finds issues or pull requests that were either created by a certain user, assigned to that user, mention that user, or were commented on by that user.
team For organizations you're a member of, finds issues or pull requests that @mention a team within the organization.
state Filter issues or pull requests based on whether they're open or closed.
labels Filters issues or pull requests based on their labels.
no Filters items missing certain metadata, such as label, milestone, or assignee
language Searches for issues or pull requests within repositories that match a certain language.
is Searches for items within repositories that match a certain state, such as open, closed, or merged
created or updated Filters issues or pull requests based on date of creation, or when they were last updated.
merged Filters pull requests based on the date when they were merged.
status Filters pull requests based on the commit status.
head or base Filters pull requests based on the branch that they came from or that they are modifying.
closed Filters issues or pull requests based on the date when they were closed.
comments Filters issues or pull requests based on the quantity of comments.
user or repo Limits searches to a specific user or repository.
project Limits searches to a specific project board in a repository or organization.
archived Filters issues or pull requests based on whether they are in an archived repository.
 */
class GithubIssueSearchQuery(
  val type: Type? = null,
  val state: String? = null,
  val repo: GithubFullPath? = null,
  val assignee: String? = null,
  val query: String? = null
) {

  fun toParameterValue(): String {
    return GithubApiSearchTermBuilder.searchQuery {
      qualifier("type", type?.toString())
      qualifier("state", state)
      qualifier("repo", repo?.fullName)
      qualifier("assignee", assignee)
      query(query)
    }
  }

  @Suppress("EnumEntryName")
  enum class Type {
    issue, pr
  }
}