// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.jetbrains.plugins.github.api.data.GHUser

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "__typename", visible = false)
@JsonSubTypes(
  JsonSubTypes.Type(name = "User", value = GHUser::class),
  JsonSubTypes.Type(name = "Team", value = GHPullRequestRequestedReviewer.Team::class)
)
interface GHPullRequestRequestedReviewer {
  // because we need scopes to access teams
  class Team : GHPullRequestRequestedReviewer {
    override fun toString(): String {
      return "Unknown Team"
    }
  }
}