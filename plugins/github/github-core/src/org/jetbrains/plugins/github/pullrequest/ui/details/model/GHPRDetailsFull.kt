// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHReaction
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import java.util.Date

data class GHPRDetailsFull(
  val id: GHPRIdentifier,
  val url: String,
  val author: GHActor,
  val createdAt: Date,
  val state: GHPullRequestState,
  val titleHtml: @NlsSafe String,
  val description: String?,
  val descriptionHtml: @NlsSafe String?,
  val headRefId: String?,
  val headRefName: String?,
  val canEditDescription: Boolean,
  val canReactDescription: Boolean,
  val canDeleteHeadRef: Boolean,
  val reactions: List<GHReaction>,
)
