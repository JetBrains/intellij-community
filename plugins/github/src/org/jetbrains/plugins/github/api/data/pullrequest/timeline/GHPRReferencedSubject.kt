// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "__typename", visible = false,
              defaultImpl = GHPRReferencedSubject::class)
@JsonSubTypes(
  JsonSubTypes.Type(name = "Issue", value = GHPRReferencedSubject.Issue::class),
  JsonSubTypes.Type(name = "PullRequest", value = GHPRReferencedSubject.PullRequest::class)
)
@NonExtendable
open class GHPRReferencedSubject(val title: @Nls String, val number: Long, val url: String) {

  class Issue(title: @Nls String, number: Long, url: String, val state: GithubIssueState)
    : GHPRReferencedSubject(title, number, url)

  class PullRequest(title: @Nls String, number: Long, url: String, val state: GHPullRequestState, val isDraft: Boolean)
    : GHPRReferencedSubject(title, number, url)
}