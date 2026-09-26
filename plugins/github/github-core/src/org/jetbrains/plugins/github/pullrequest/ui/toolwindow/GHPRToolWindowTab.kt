// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.ui.toolwindow.ReviewTab
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

@ApiStatus.Experimental
sealed interface GHPRToolWindowTab : ReviewTab {
  @ApiStatus.Experimental
  data class PullRequest(val prId: GHPRIdentifier) : GHPRToolWindowTab {
    override val id: @NonNls String = "Review Details: ${prId.number}"
    override val reuseTabOnRequest: Boolean = true
  }

  @ApiStatus.Experimental
  data object NewPullRequest : GHPRToolWindowTab {
    override val id: @NonNls String = "New Pull Request"
    override val reuseTabOnRequest: Boolean = true
  }
}