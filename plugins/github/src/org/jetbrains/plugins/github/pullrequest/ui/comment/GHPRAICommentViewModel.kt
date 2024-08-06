// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.github.ai.assistedReview.GHPRAICommentChatViewModel

interface GHPRAICommentViewModel {
  val key: Any
  val textHtml: StateFlow<String>

  val isBusy: StateFlow<Boolean>

  val isVisible: StateFlow<Boolean>
  val location: DiffLineLocation?

  val chatVm: MutableStateFlow<GHPRAICommentChatViewModel?>

  fun startChat()

  /**
   * Create the actual comment on the server
   */
  fun accept()

  /**
   * Disacrd the comment
   */
  fun reject()
}