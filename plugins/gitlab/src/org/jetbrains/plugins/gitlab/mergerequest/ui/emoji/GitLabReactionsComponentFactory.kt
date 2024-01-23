// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.emoji

import com.intellij.collaboration.ui.ComponentListPanelFactory
import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.collaboration.ui.codereview.reactions.CodeReviewReactionsUIUtil
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.*
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.hover.addHoverAndPressStateListener
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.CollaborationToolsIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabReaction
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabReactionImpl
import java.awt.Cursor
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants

internal object GitLabReactionsComponentFactory {
  private val BORDER = RoundedLineBorder(CodeReviewColorUtil.Reaction.border,
                                         CodeReviewReactionsUIUtil.BUTTON_BORDER_ROUNDNESS)
  private val HOVERED_BORDER = RoundedLineBorder(CodeReviewColorUtil.Reaction.borderHovered,
                                                 CodeReviewReactionsUIUtil.BUTTON_BORDER_ROUNDNESS)
  private val PRESSED_BORDER = RoundedLineBorder(CodeReviewColorUtil.Reaction.borderPressed,
                                                 CodeReviewReactionsUIUtil.BUTTON_BORDER_ROUNDNESS)

  fun create(cs: CoroutineScope, reactionsVm: GitLabReactionsViewModel): JComponent {
    return ComponentListPanelFactory.createHorizontal(
      cs,
      reactionsVm.reactionsWithInfo.map { it.keys.toList() },
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
    val emojiLabel = JLabel().apply {
      horizontalAlignment = SwingConstants.CENTER
      border = JBUI.Borders.empty(CodeReviewReactionsUIUtil.BUTTON_PADDING)
      bindTextIn(cs, reactionsVm.reactionsWithInfo.map { reactionsWithInfo ->
        val (count, _) = reactionsWithInfo[reaction] ?: return@map ""
        if (count > 0) "${reaction.emoji} $count" else reaction.emoji
      })
    }
    val layout = SizeRestrictedSingleComponentLayout().apply {
      val dimension = DimensionRestrictions.ScalingConstant(
        CodeReviewReactionsUIUtil.BUTTON_WIDTH,
        CodeReviewReactionsUIUtil.BUTTON_HEIGHT
      )
      prefSize = dimension
      maxSize = dimension
    }

    return RoundedPanel(layout, arc = CodeReviewReactionsUIUtil.BUTTON_ROUNDNESS).apply {
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
      add(emojiLabel)
    }
  }

  private fun createReactionPickerButton(cs: CoroutineScope, reactionsVm: GitLabReactionsViewModel): JComponent {
    val reactionPickerLabel = JLabel().apply {
      horizontalAlignment = SwingConstants.CENTER
      border = JBUI.Borders.empty(CodeReviewReactionsUIUtil.Picker.BUTTON_PADDING)
      icon = CollaborationToolsIcons.AddEmoji
    }
    val layout = SizeRestrictedSingleComponentLayout().apply {
      val dimension = DimensionRestrictions.ScalingConstant(
        CodeReviewReactionsUIUtil.Picker.BUTTON_WIDTH,
        CodeReviewReactionsUIUtil.Picker.BUTTON_HEIGHT
      )
      prefSize = dimension
      maxSize = dimension
    }

    return RoundedPanel(layout, arc = CodeReviewReactionsUIUtil.BUTTON_ROUNDNESS).apply {
      UIUtil.setCursor(this, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
      border = BORDER
      background = CodeReviewColorUtil.Reaction.background
      addHoverAndPressStateListener(
        comp = this,
        hoveredStateCallback = { component, isHovered ->
          component as JComponent
          component.background = if (isHovered) CodeReviewColorUtil.Reaction.backgroundHovered else CodeReviewColorUtil.Reaction.background
          component.border = if (isHovered) HOVERED_BORDER else BORDER
        },
        pressedStateCallback = { component, isPressed ->
          component as JComponent
          if (!isPressed) return@addHoverAndPressStateListener
          showReactionPickerPopup(cs, reactionsVm, component)
        }
      )
      add(reactionPickerLabel)
    }
  }

  private fun showReactionPickerPopup(cs: CoroutineScope, reactionsVm: GitLabReactionsViewModel, component: JComponent) {
    cs.launch {
      val availableReactions = reactionsVm.availableParsedEmojis.await().map(::GitLabReactionImpl)
      var popup: JBPopup? = null
      val reactionPicker = GitLabReactionsPickerComponentFactory.create(availableReactions) { reaction ->
        reactionsVm.toggle(reaction)
        popup?.cancel()
      }
      popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(reactionPicker, reactionPicker)
        .setResizable(false)
        .setFocusable(true)
        .setRequestFocus(true)
        .createPopup()

      popup.showUnderneathOf(component)
    }
  }
}