// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import kotlinx.coroutines.flow.StateFlow

interface GHPRAICommentViewModel {
  val key: Any
  val textHtml: StateFlow<String>

  val isBusy: StateFlow<Boolean>

  /**
   * Create the actual comment on the server
   */
  fun accept()

  /**
   * Disacrd the comment
   */
  fun reject()
}