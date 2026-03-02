// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import com.intellij.openapi.util.Key
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRMentionableUsersProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.ui.icons.GHAvatarIconsProvider

interface GHViewModelWithTextCompletion {

  fun withMentionCompletionModel(consumer: (GHViewModelMentionCompletion) -> Unit)

  companion object {
    val MENTIONS_COMPLETION_KEY: Key<GHViewModelWithTextCompletion> = Key.create("GitHub.Chat.MentionsContextViewModel")
  }
}

internal class GHViewModelWithTextCompletionImpl(
  parentCs: CoroutineScope,
  private val mentionableUsersProvider: GHPRMentionableUsersProvider,
  private val reviewDataProvider: GHPRReviewDataProvider,
  private val avatarIconsProvider: GHAvatarIconsProvider,
) : GHViewModelWithTextCompletion {

  private val cs = parentCs.childScope(javaClass.name)

  override fun withMentionCompletionModel(consumer: (GHViewModelMentionCompletion) -> Unit) {
    val vmCs = cs.childScope(GHViewModelMentionCompletionImpl::class.java.name)
    val vm = GHViewModelMentionCompletionImpl(vmCs, mentionableUsersProvider, reviewDataProvider, avatarIconsProvider)
    try {
      consumer(vm)
    }
    finally {
      vmCs.cancel()
    }
  }
}