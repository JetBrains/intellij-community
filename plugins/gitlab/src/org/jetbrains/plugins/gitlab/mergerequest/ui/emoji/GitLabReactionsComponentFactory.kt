// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.emoji

import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.collaboration.ui.codereview.reactions.CodeReviewReactionsUIUtil
import com.intellij.collaboration.ui.util.CodeReviewColorUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.RoundedLineBorder
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants

internal object GitLabReactionsComponentFactory {
  private val BORDER = RoundedLineBorder(CodeReviewColorUtil.Reaction.border, CodeReviewReactionsUIUtil.BORDER_ROUNDNESS)
  private val PRESSED_BORDER = RoundedLineBorder(CodeReviewColorUtil.Reaction.borderPressed, CodeReviewReactionsUIUtil.BORDER_ROUNDNESS)

  fun create(reactionsVm: GitLabReactionsViewModel): JComponent {
    return HorizontalListPanel(gap = CodeReviewReactionsUIUtil.HORIZONTAL_GAP).apply {
      reactionsVm.reactions
        .map { emoji -> createReactionLabel(emoji, reactionsVm) }
        .forEach(::add)
    }
  }

  private fun createReactionLabel(emoji: @NlsSafe String, reactionsVm: GitLabReactionsViewModel): JComponent {
    val emojiCount = reactionsVm.getReactionCount(emoji)
    val emojiText = if (emojiCount > 0) "$emoji $emojiCount" else emoji
    val emojiLabel = JLabel(emojiText, SwingConstants.CENTER)
    return RoundedPanel(BorderLayout(), arc = CodeReviewReactionsUIUtil.ROUNDNESS).apply {
      val isReacted = reactionsVm.isReactedWithCurrentUser(emoji)
      background = if (isReacted) CodeReviewColorUtil.Reaction.backgroundPressed else CodeReviewColorUtil.Reaction.background
      border = if (isReacted) PRESSED_BORDER else BORDER

      add(emojiLabel, BorderLayout.CENTER)
    }
  }
}