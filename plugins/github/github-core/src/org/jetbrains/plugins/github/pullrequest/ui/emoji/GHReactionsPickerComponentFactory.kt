// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.emoji

import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.github.api.data.GHReactionContent
import java.awt.event.ActionListener
import javax.swing.JComponent

object GHReactionsPickerComponentFactory {
  private const val EMOJI_PICKER_GAP = 7
  private const val EMOJI_PICKER_BORDER = 7
  private const val EMOJI_SIZE = 26
  private const val EMOJI_PADDING = 5

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
    val icon = reactionsVm.reactionIconsProvider.getIcon(reaction, EMOJI_SIZE)
    return InlineIconButton(icon).apply {
      isFocusable = false
      withBackgroundHover = true
      actionListener = ActionListener { onClick(reaction) }
      margin = JBInsets.create(EMOJI_PADDING, EMOJI_PADDING)
    }
  }
}