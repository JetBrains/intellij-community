// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.emoji

import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.emoji.ReactionLabel
import com.intellij.collaboration.ui.codereview.reactions.CodeReviewReactionsUIUtil
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.CodeReviewColorUtil
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.hover.addHoverAndPressStateListener
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.github.api.data.GHReactionContent
import javax.swing.JComponent

object GHReactionsPickerComponentFactory {
  private const val EMOJI_PICKER_GAP = 2
  private const val EMOJI_PICKER_BORDER = 2

  fun showPopup(reactionsVm: GHReactionsViewModel, parentComponent: JComponent) {
    var popup: JBPopup? = null
    val emojiPicker = create(reactionsVm, GHReactionContent.entries) { reaction ->
      reactionsVm.toggle(reaction)
      popup?.cancel()
    }
    popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(emojiPicker, emojiPicker)
      .setResizable(false)
      .setFocusable(true)
      .setRequestFocus(true)
      .createPopup()

    popup.showUnderneathOf(parentComponent)
  }

  private fun create(
    reactionsVm: GHReactionsViewModel,
    reactions: List<GHReactionContent>,
    onClick: (GHReactionContent) -> Unit
  ): JComponent {
    return HorizontalListPanel(gap = EMOJI_PICKER_GAP).apply {
      border = JBUI.Borders.empty(EMOJI_PICKER_BORDER)
      reactions
        .map { reaction -> createEmojiLabel(reactionsVm, reaction, onClick) }
        .forEach { label -> add(label) }
    }
  }

  private fun createEmojiLabel(
    reactionsVm: GHReactionsViewModel,
    reaction: GHReactionContent,
    onClick: (GHReactionContent) -> Unit
  ): JComponent {
    val layout = SizeRestrictedSingleComponentLayout().apply {
      val dimension = DimensionRestrictions.ScalingConstant(
        CodeReviewReactionsUIUtil.Picker.EMOJI_WIDTH,
        CodeReviewReactionsUIUtil.Picker.EMOJI_HEIGHT
      )
      prefSize = dimension
      maxSize = dimension
    }
    return ReactionLabel(
      layout,
      icon = reactionsVm.reactionIconsProvider.getIcon(reaction, CodeReviewReactionsUIUtil.Picker.EMOJI_ICON_SIZE),
      onClick = { onClick(reaction) },
      labelInitializer = { border = JBUI.Borders.empty(12, 4) }
    ).apply {
      val isReacted = reactionsVm.reactionsWithInfo.value[reaction]?.isReactedByCurrentUser ?: false
      background = if (isReacted) CodeReviewColorUtil.Reaction.backgroundHovered else null
      border = JBUI.Borders.empty()
      addHoverAndPressStateListener(this, hoveredStateCallback = { component, isHovered ->
        component.background = if (isReacted || isHovered) CodeReviewColorUtil.Reaction.backgroundHovered else null
      })
    }
  }
}