// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.plugins.github.api.data.*
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import java.util.*

open class GHPullRequestShort(id: String,
                              val url: String,
                              override val number: Long,
                              val title: String,
                              val state: GHPullRequestState,
                              val author: GHActor?,
                              val createdAt: Date,
                              @JsonProperty("assignees") assignees: GHNodes<GHUser>,
                              @JsonProperty("labels") labels: GHNodes<GHLabel>,
                              val viewerCanUpdate: Boolean,
                              val viewerDidAuthor: Boolean) : GHNode(id), GHPRIdentifier {

  @JsonIgnore
  val assignees = assignees.nodes

  @JsonIgnore
  val labels = labels.nodes

  override fun toString(): String = "#$number $title"
}
