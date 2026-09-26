// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.util.IncrementallyComputedValue
import com.intellij.collaboration.util.collectIncrementallyTo
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRMentionableUsersProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.ui.icons.GHAvatarIconsProvider

/**
 * Short-lived view model for mentions variants completion.
 */
interface GHViewModelMentionCompletion {
  val pullRequestParticipants: StateFlow<IncrementallyComputedValue<List<GHUser>>>
  val mentionableUsers: StateFlow<IncrementallyComputedValue<List<GHUser>>>
  val avatarIconsProvider: GHAvatarIconsProvider
}

internal class GHViewModelMentionCompletionImpl(
  parentCs: CoroutineScope,
  private val mentionableUsersProvider: GHPRMentionableUsersProvider,
  private val reviewDataProvider: GHPRReviewDataProvider,
  override val avatarIconsProvider: GHAvatarIconsProvider,
) : GHViewModelMentionCompletion {

  private val cs = parentCs.childScope(javaClass.name)

  @OptIn(ExperimentalCoroutinesApi::class)
  override val pullRequestParticipants: StateFlow<IncrementallyComputedValue<List<GHUser>>> =
    reviewDataProvider.participantsNeedReloadSignal.withInitial(Unit).transformLatest {
      reviewDataProvider.participantsBatches().collectIncrementallyTo(this)
    }.stateIn(cs, SharingStarted.Lazily, IncrementallyComputedValue.Companion.loading())

  @OptIn(ExperimentalCoroutinesApi::class)
  override val mentionableUsers: StateFlow<IncrementallyComputedValue<List<GHUser>>> = flow {
    mentionableUsersProvider.getMentionableUsersBatches().collectIncrementallyTo(this)
  }.stateIn(cs, SharingStarted.Lazily, IncrementallyComputedValue.Companion.loading())
}