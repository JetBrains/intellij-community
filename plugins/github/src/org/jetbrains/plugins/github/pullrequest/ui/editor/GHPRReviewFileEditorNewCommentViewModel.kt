// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

interface GHPRReviewFileEditorNewCommentViewModel : GHPRReviewNewCommentEditorViewModel {
  val line: Int
}

internal class GHPRReviewFileEditorNewCommentViewModelImpl(override val line: Int,
                                                           private val sharedVm: GHPRReviewNewCommentEditorViewModel)
  : GHPRReviewFileEditorNewCommentViewModel, GHPRReviewNewCommentEditorViewModel by sharedVm {
  private val _focusRequestsChannel = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
  override val focusRequests: Flow<Unit> get() = _focusRequestsChannel.receiveAsFlow()

  override fun requestFocus() {
    _focusRequestsChannel.trySend(Unit)
  }
}