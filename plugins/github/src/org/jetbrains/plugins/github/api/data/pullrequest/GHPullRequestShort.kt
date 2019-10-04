// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.plugins.github.api.data.*
import java.util.*

open class GHPullRequestShort(id: String,
                              val url: String,
                              val number: Long,
                              val title: String,
                              val state: GHPullRequestState,
                              val author: GHActor?,
                              val createdAt: Date,
                              @JsonProperty("assignees") assignees: GHNodes<GHUser>,
                              @JsonProperty("labels") labels: GHNodes<GHLabel>) : GHNode(id) {

  @JsonIgnore
  val assignees = assignees.nodes
  @JsonIgnore
  val labels = labels.nodes
}
