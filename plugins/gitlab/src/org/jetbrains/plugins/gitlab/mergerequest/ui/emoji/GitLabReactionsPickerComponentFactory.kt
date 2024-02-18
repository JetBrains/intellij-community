// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.emoji

import com.intellij.collaboration.ui.TransparentScrollPane
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.reactions.CodeReviewReactionsUIUtil
import com.intellij.collaboration.ui.codereview.reactions.ReactionLabel
import com.intellij.collaboration.ui.codereview.reactions.VARIATION_SELECTOR
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.CodeReviewColorUtil
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.collaboration.ui.util.popup.awaitClose
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.hover.addHoverAndPressStateListener
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.WrapLayout
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabReaction
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabReactionImpl
import java.awt.FlowLayout
import java.util.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal object GitLabReactionsPickerComponentFactory {
  suspend fun showPopup(reactionsVm: GitLabReactionsViewModel, component: JComponent) {
    val availableReactions = reactionsVm.availableParsedEmojis.await().map(::GitLabReactionImpl)
    var popup: JBPopup? = null
    val reactionPicker = create(availableReactions) { reaction ->
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
    popup.awaitClose()
  }

  private fun create(reactions: List<GitLabReaction>, onClick: (GitLabReaction) -> Unit): JComponent {
    val reactionsWithCategory = VerticalListPanel().apply {
      reactions
        .groupBy({ it.category.orEmpty() }, { it })
        .forEach { (category, reactions) ->
          add(createCategoryComponent(category))
          add(createReactionsBlock(reactions, onClick))
        }
    }

    return TransparentScrollPane(reactionsWithCategory).apply {
      preferredSize = JBUI.size(CodeReviewReactionsUIUtil.Picker.WIDTH, CodeReviewReactionsUIUtil.Picker.HEIGHT)
    }
  }

  private fun createCategoryComponent(name: @NlsSafe String): JComponent {
    val capitalizedName = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } // NON-NLS
    return JLabel(capitalizedName).apply {
      isOpaque = false
      font = font.deriveFont(font.size.toFloat() * 1.4f)
      border = JBUI.Borders.empty(10, 10, 5, 5)
    }
  }

  private fun createReactionsBlock(reactions: List<GitLabReaction>, onClick: (GitLabReaction) -> Unit): JComponent {
    return JPanel(WrapLayout(FlowLayout.LEFT, 0, 0)).apply {
      isOpaque = false
      border = JBUI.Borders.empty(
        CodeReviewReactionsUIUtil.Picker.BLOCK_PADDING,
        CodeReviewReactionsUIUtil.Picker.BLOCK_PADDING,
        CodeReviewReactionsUIUtil.Picker.BLOCK_PADDING,
        0
      )
      reactions
        .map { reaction -> createReactionLabel(reaction) { onClick(it) } }
        .forEach { component -> add(component) }
    }
  }

  private fun createReactionLabel(reaction: GitLabReaction, onClick: (GitLabReaction) -> Unit): JComponent {
    val layout = SizeRestrictedSingleComponentLayout().apply {
      val dimension = DimensionRestrictions.ScalingConstant(
        CodeReviewReactionsUIUtil.Picker.EMOJI_WIDTH,
        CodeReviewReactionsUIUtil.Picker.EMOJI_HEIGHT
      )
      prefSize = dimension
      maxSize = dimension
    }
    return ReactionLabel(layout, onClick = { onClick(reaction) }) {
      text = reaction.emoji + VARIATION_SELECTOR
    }.apply {
      addHoverAndPressStateListener(this, hoveredStateCallback = { component, isHovered ->
        component.background = if (isHovered) CodeReviewColorUtil.Reaction.backgroundHovered else null
      })
    }
  }
}