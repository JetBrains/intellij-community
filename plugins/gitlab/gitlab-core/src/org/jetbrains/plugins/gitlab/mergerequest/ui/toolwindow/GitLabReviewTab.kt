// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.collaboration.ui.toolwindow.ReviewTab
import org.jetbrains.annotations.NonNls

internal sealed interface GitLabReviewTab : ReviewTab {
  data class ReviewSelected(val mrIid: String) : GitLabReviewTab {
    override val id: @NonNls String = "ReviewDetails!${mrIid}"
    override val reuseTabOnRequest: Boolean = true
  }

  object NewMergeRequest : GitLabReviewTab {
    override val id: @NonNls String = "New Merge Request"
    override val reuseTabOnRequest: Boolean = false
  }
}