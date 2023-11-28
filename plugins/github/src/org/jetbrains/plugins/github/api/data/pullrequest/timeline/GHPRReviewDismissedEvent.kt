// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline

import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.github.api.data.GHActor
import java.util.*

data class GHPRReviewDismissedEvent(override val actor: GHActor?,
                                    override val createdAt: Date,
                                    val dismissalMessageHTML: @NlsSafe String?,
                                    @JsonProperty("review") private val review: ReviewAuthor?)
  : GHPRTimelineEvent.Complex {

  val reviewAuthor = review?.author

  class ReviewAuthor(val author: GHActor?)
}
