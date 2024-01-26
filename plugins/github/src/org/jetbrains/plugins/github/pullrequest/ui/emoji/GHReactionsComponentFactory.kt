// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.emoji

import com.intellij.collaboration.ui.ComponentListPanelFactory
import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.collaboration.ui.codereview.reactions.CodeReviewReactionsUIUtil
import com.intellij.collaboration.ui.util.CodeReviewColorUtil
import com.intellij.ui.RoundedLineBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.github.api.data.GHReaction
import java.awt.BorderLayout
import java.awt.Cursor
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants

internal object GHReactionsComponentFactory {
  private val EMOJI_BORDER = RoundedLineBorder(CodeReviewColorUtil.Reaction.border, CodeReviewReactionsUIUtil.BUTTON_ROUNDNESS)

  fun create(cs: CoroutineScope, reactionsVm: GHReactionsViewModel): JComponent {
    return ComponentListPanelFactory.createHorizontal(
      cs,
      reactionsVm.reactions,
      gap = CodeReviewReactionsUIUtil.HORIZONTAL_GAP,
      componentFactory = { reaction -> createReactionLabel(reactionsVm, reaction) }
    )
  }

  private fun createReactionLabel(reactionsVm: GHReactionsViewModel, reaction: GHReaction): JComponent {
    val emojiLabel = JLabel().apply {
      horizontalAlignment = SwingConstants.CENTER
      border = JBUI.Borders.empty(CodeReviewReactionsUIUtil.ICON_BORDER_SIZE)
      icon = reactionsVm.reactionIconsProvider.getIcon(reaction.content, CodeReviewReactionsUIUtil.ICON_SIZE)
      text = "${reactionsVm.getReactionCount(reaction)}"
    }

    return RoundedPanel(BorderLayout(), CodeReviewReactionsUIUtil.BUTTON_ROUNDNESS + CodeReviewReactionsUIUtil.ICON_BORDER_SIZE).apply {
      UIUtil.setCursor(this, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
      border = if (reactionsVm.isReactedWithCurrentUser(reaction)) EMOJI_BORDER else JBUI.Borders.empty()
      add(emojiLabel, BorderLayout.CENTER)
    }
  }
}