// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.emoji

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.ComponentListPanelFactory
import com.intellij.collaboration.ui.codereview.reactions.CodeReviewReactionComponent
import com.intellij.collaboration.ui.codereview.reactions.CodeReviewReactionPillPresentation
import com.intellij.collaboration.ui.codereview.reactions.CodeReviewReactionsUIUtil
import com.intellij.collaboration.ui.util.bindVisibilityIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabReaction
import javax.swing.Icon
import javax.swing.JComponent

internal object GitLabReactionsComponentFactory {
  fun create(cs: CoroutineScope, reactionsVm: GitLabReactionsViewModel): JComponent {
    return ComponentListPanelFactory.createHorizontal(
      cs,
      reactionsVm.reactionsWithInfo.mapState { it.keys.toList() },
      gap = CodeReviewReactionsUIUtil.HORIZONTAL_GAP,
      componentFactory = { reaction -> createReactionLabel(this, reaction, reactionsVm) }
    ).apply {
      val reactionPicker = createReactionPickerButton(cs, reactionsVm).apply {
        bindVisibilityIn(cs, reactionsVm.reactionsWithInfo.map { it.isNotEmpty() })
      }
      add(reactionPicker, -1)
    }
  }

  private fun createReactionLabel(cs: CoroutineScope, reaction: GitLabReaction, reactionsVm: GitLabReactionsViewModel): JComponent {
    val state: Flow<CodeReviewReactionPillPresentation> = reactionsVm.reactionsWithInfo.mapNotNull { reactionsWithInfo ->
      reactionsWithInfo[reaction]?.let {
        Presentation(reaction, it)
      }
    }
    return CodeReviewReactionComponent.createReactionButtonIn(cs, state) {
      reactionsVm.toggle(reaction)
    }
  }

  private fun createReactionPickerButton(cs: CoroutineScope, reactionsVm: GitLabReactionsViewModel): JComponent =
    CodeReviewReactionComponent.createNewReactionButton {
      showReactionPickerPopup(cs, reactionsVm, it)
    }

  private fun showReactionPickerPopup(cs: CoroutineScope, reactionsVm: GitLabReactionsViewModel, component: JComponent) {
    cs.launch {
      GitLabReactionsPickerComponentFactory.showPopup(reactionsVm, component)
    }
  }

  private data class Presentation(
    private val reaction: GitLabReaction,
    private val reactionInfo: ReactionInfo
  ) : CodeReviewReactionPillPresentation {
    override val reactionName: String = reaction.name
    override val reactors: List<String> = reactionInfo.users.map(GitLabUserDTO::name)
    override val isOwnReaction: Boolean = reactionInfo.isReactedByCurrentUser

    override fun getIcon(size: Int): Icon = CodeReviewReactionsUIUtil.createUnicodeEmojiIcon(reaction.emoji, size)
  }
}