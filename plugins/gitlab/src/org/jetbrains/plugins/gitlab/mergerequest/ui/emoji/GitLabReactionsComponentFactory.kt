// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.emoji

import com.intellij.collaboration.ui.ComponentListPanelFactory
import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.collaboration.ui.codereview.reactions.CodeReviewReactionsUIUtil
import com.intellij.collaboration.ui.util.CodeReviewColorUtil
import com.intellij.collaboration.ui.util.bindBackgroundIn
import com.intellij.collaboration.ui.util.bindBorderIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.hover.addHoverAndPressStateListener
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabReaction
import java.awt.BorderLayout
import java.awt.Cursor
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants

internal object GitLabReactionsComponentFactory {
  private val BORDER = RoundedLineBorder(CodeReviewColorUtil.Reaction.border, CodeReviewReactionsUIUtil.BORDER_ROUNDNESS)
  private val HOVERED_BORDER = RoundedLineBorder(CodeReviewColorUtil.Reaction.borderHovered, CodeReviewReactionsUIUtil.BORDER_ROUNDNESS)
  private val PRESSED_BORDER = RoundedLineBorder(CodeReviewColorUtil.Reaction.borderPressed, CodeReviewReactionsUIUtil.BORDER_ROUNDNESS)

  fun create(cs: CoroutineScope, reactionsVm: GitLabReactionsViewModel): JComponent {
    return ComponentListPanelFactory.createHorizontal(
      cs,
      reactionsVm.reactionsWithInfo.map { it.keys.toList() },
      gap = CodeReviewReactionsUIUtil.HORIZONTAL_GAP,
      componentFactory = { reaction -> createReactionLabel(this, reaction, reactionsVm) }
    )
  }

  private fun createReactionLabel(cs: CoroutineScope, reaction: GitLabReaction, reactionsVm: GitLabReactionsViewModel): JComponent {
    val emojiLabel = JLabel().apply {
      horizontalAlignment = SwingConstants.CENTER
      border = JBUI.Borders.empty(CodeReviewReactionsUIUtil.BORDER)
      bindTextIn(cs, reactionsVm.reactionsWithInfo.map { reactionsWithInfo ->
        val (count, _) = reactionsWithInfo[reaction] ?: return@map ""
        if (count > 0) "${reaction.emoji} $count" else reaction.emoji
      })
    }
    return RoundedPanel(BorderLayout(), arc = CodeReviewReactionsUIUtil.ROUNDNESS).apply {
      UIUtil.setCursor(this, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))

      var currentBorder: RoundedLineBorder = BORDER
      bindBorderIn(cs, reactionsVm.reactionsWithInfo.map { reactionsWithInfo ->
        val (_, isReactedByCurrentUser) = reactionsWithInfo[reaction] ?: return@map BORDER
        currentBorder = if (isReactedByCurrentUser) PRESSED_BORDER else BORDER
        currentBorder
      })

      var currentBackground: JBColor = CodeReviewColorUtil.Reaction.background
      bindBackgroundIn(cs, reactionsVm.reactionsWithInfo.map { reactionsWithInfo ->
        val (_, isReactedByCurrentUser) = reactionsWithInfo[reaction] ?: return@map CodeReviewColorUtil.Reaction.background
        currentBackground = if (isReactedByCurrentUser) CodeReviewColorUtil.Reaction.backgroundPressed
        else CodeReviewColorUtil.Reaction.background
        currentBackground
      })

      addHoverAndPressStateListener(
        comp = this,
        hoveredStateCallback = { component, isHovered ->
          component as JComponent
          val isReacted = reactionsVm.reactionsWithInfo.value[reaction]?.isReactedByCurrentUser ?: false
          component.border = if (isHovered) {
            if (isReacted) PRESSED_BORDER else HOVERED_BORDER
          }
          else currentBorder
          component.background = if (isHovered) CodeReviewColorUtil.Reaction.backgroundHovered else currentBackground
        },
        pressedStateCallback = { component, isPressed ->
          if (!isPressed) return@addHoverAndPressStateListener
          reactionsVm.toggle(reaction)
        }
      )
      add(emojiLabel, BorderLayout.CENTER)
    }
  }
}