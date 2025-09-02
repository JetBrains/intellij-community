// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.emoji

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
import org.jetbrains.plugins.github.api.data.GHReactionContent
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.presentableName
import javax.swing.Icon
import javax.swing.JComponent

internal object GHReactionsComponentFactory {
  fun create(cs: CoroutineScope, reactionsVm: GHReactionsViewModel): JComponent {
    return ComponentListPanelFactory.createHorizontal(
      cs,
      reactionsVm.reactionsWithInfo.mapState { it.keys.toList() },
      gap = CodeReviewReactionsUIUtil.HORIZONTAL_GAP,
      componentFactory = { reaction -> createReactionLabel(this, reactionsVm, reaction) }
    ).apply {
      val reactionPicker = createReactionPickerButton(reactionsVm).apply {
        bindVisibilityIn(cs, reactionsVm.reactionsWithInfo.map { it.isNotEmpty() })
      }
      add(reactionPicker, -1)
    }
  }

  private fun createReactionLabel(cs: CoroutineScope, reactionsVm: GHReactionsViewModel, reaction: GHReactionContent): JComponent {
    val state: Flow<CodeReviewReactionPillPresentation> = reactionsVm.reactionsWithInfo.mapNotNull { reactionsWithInfo ->
      reactionsWithInfo[reaction]?.let { Presentation(reactionsVm, reaction, it) }
    }
    return CodeReviewReactionComponent.createReactionButtonIn(cs, state) {
      reactionsVm.toggle(reaction)
    }
  }

  private fun createReactionPickerButton(reactionsVm: GHReactionsViewModel): JComponent =
    CodeReviewReactionComponent.createNewReactionButton {
      GHReactionsPickerComponentFactory.showPopup(reactionsVm, it)
    }

  private class Presentation(
    private val reactionsVm: GHReactionsViewModel,
    private val reaction: GHReactionContent,
    private val reactionInfo: ReactionInfo
  ) : CodeReviewReactionPillPresentation {
    override val reactionName: String = reaction.presentableName
    override val reactors: List<String> = reactionInfo.users.map(GHUser::getPresentableName)
    override val isOwnReaction: Boolean = reactionInfo.isReactedByCurrentUser

    override fun getIcon(size: Int): Icon = reactionsVm.reactionIconsProvider.getIcon(reaction, size)

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Presentation) return false

      if (reaction != other.reaction) return false
      if (reactionInfo != other.reactionInfo) return false

      return true
    }

    override fun hashCode(): Int {
      var result = reaction.hashCode()
      result = 31 * result + reactionInfo.hashCode()
      return result
    }
  }
}