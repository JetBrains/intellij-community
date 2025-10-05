// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.diff

import com.intellij.collaboration.async.mapState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentPosition
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModel

interface GHPRNewCommentDiffViewModel : GHPRReviewNewCommentEditorViewModel {
  val location: StateFlow<GHPRReviewCommentLocation>
}

internal class GHPRNewCommentDiffViewModelImpl(
  position: StateFlow<GHPRReviewCommentPosition>,
  private val sharedVm: GHPRReviewNewCommentEditorViewModel,
)
  : GHPRNewCommentDiffViewModel, GHPRReviewNewCommentEditorViewModel by sharedVm {
  private val _focusRequestsChannel = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
  override val focusRequests: Flow<Unit> get() = _focusRequestsChannel.receiveAsFlow()
  override val location = position.mapState { it.location }
  override fun requestFocus() {
    _focusRequestsChannel.trySend(Unit)
  }
}