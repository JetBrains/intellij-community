// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.collaboration.ui.toolwindow.ReviewTab
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId

internal sealed interface GitLabReviewTab : ReviewTab {
  data class ReviewSelected(val reviewId: GitLabMergeRequestId) : GitLabReviewTab {
    override val id: @NonNls String = "ReviewDetails!${reviewId.iid}"
    override val displayName: @NlsSafe String = "!${reviewId.iid}"

    override val reuseTabOnRequest: Boolean = true
  }
}