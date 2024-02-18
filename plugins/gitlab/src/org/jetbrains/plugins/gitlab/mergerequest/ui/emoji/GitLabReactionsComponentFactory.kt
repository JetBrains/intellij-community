// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.emoji

import com.intellij.collaboration.ui.ComponentListPanelFactory
import com.intellij.collaboration.ui.codereview.reactions.CodeReviewReactionsUIUtil
import com.intellij.collaboration.ui.codereview.reactions.ReactionLabel
import com.intellij.collaboration.ui.codereview.reactions.VARIATION_SELECTOR
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.*
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.hover.addHoverAndPressStateListener
import com.intellij.util.ui.JBUI
import icons.CollaborationToolsIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabReaction
import javax.swing.JComponent

internal object GitLabReactionsComponentFactory {
  private val BORDER = RoundedLineBorder(CodeReviewColorUtil.Reaction.border, CodeReviewReactionsUIUtil.BUTTON_ROUNDNESS)
  private val HOVERED_BORDER = RoundedLineBorder(CodeReviewColorUtil.Reaction.borderHovered, CodeReviewReactionsUIUtil.BUTTON_ROUNDNESS)
  private val PRESSED_BORDER = RoundedLineBorder(CodeReviewColorUtil.Reaction.borderPressed, CodeReviewReactionsUIUtil.BUTTON_ROUNDNESS)

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
    val layout = SizeRestrictedSingleComponentLayout().apply {
      val dimension = DimensionRestrictions.ScalingConstant(
        height = CodeReviewReactionsUIUtil.BUTTON_HEIGHT
      )
      prefSize = dimension
      maxSize = dimension
    }
    return ReactionLabel(layout, onClick = { reactionsVm.toggle(reaction) }) {
      bindTextIn(cs, reactionsVm.reactionsWithInfo.map { reactionsWithInfo ->
        val (count, _) = reactionsWithInfo[reaction] ?: return@map ""
        if (count > 0) "${reaction.emoji + VARIATION_SELECTOR} $count" else reaction.emoji + VARIATION_SELECTOR
      })
    }.apply {
      var currentBorder: RoundedLineBorder = BORDER
      border = currentBorder
      bindBorderIn(cs, reactionsVm.reactionsWithInfo.map { reactionsWithInfo ->
        val (_, isReactedByCurrentUser) = reactionsWithInfo[reaction] ?: return@map BORDER
        currentBorder = if (isReactedByCurrentUser) PRESSED_BORDER else BORDER
        currentBorder
      })
      var currentBackground: JBColor = CodeReviewColorUtil.Reaction.background
      background = currentBackground
      bindBackgroundIn(cs, reactionsVm.reactionsWithInfo.map { reactionsWithInfo ->
        val (_, isReactedByCurrentUser) = reactionsWithInfo[reaction] ?: return@map CodeReviewColorUtil.Reaction.background
        currentBackground = if (isReactedByCurrentUser) CodeReviewColorUtil.Reaction.backgroundPressed
        else CodeReviewColorUtil.Reaction.background
        currentBackground
      })
      addHoverAndPressStateListener(this, hoveredStateCallback = { component, isHovered ->
        component as JComponent
        val isReacted = reactionsVm.reactionsWithInfo.value[reaction]?.isReactedByCurrentUser ?: false
        component.border = if (isHovered) {
          if (isReacted) PRESSED_BORDER else HOVERED_BORDER
        }
        else currentBorder
        component.background = if (isHovered) CodeReviewColorUtil.Reaction.backgroundHovered else currentBackground
      })
    }
  }

  private fun createReactionPickerButton(cs: CoroutineScope, reactionsVm: GitLabReactionsViewModel): JComponent {
    val layout = SizeRestrictedSingleComponentLayout().apply {
      val dimension = DimensionRestrictions.ScalingConstant(
        CodeReviewReactionsUIUtil.Picker.BUTTON_WIDTH,
        CodeReviewReactionsUIUtil.Picker.BUTTON_HEIGHT
      )
      prefSize = dimension
      maxSize = dimension
    }
    return ReactionLabel(
      layout,
      icon = CollaborationToolsIcons.AddEmoji,
      onClick = { component -> showReactionPickerPopup(cs, reactionsVm, component) },
      labelInitializer = { border = JBUI.Borders.empty(CodeReviewReactionsUIUtil.Picker.BUTTON_PADDING) }
    ).apply {
      border = BORDER
      background = CodeReviewColorUtil.Reaction.background
      addHoverAndPressStateListener(this, hoveredStateCallback = { component, isHovered ->
        component as JComponent
        component.background = if (isHovered) CodeReviewColorUtil.Reaction.backgroundHovered else CodeReviewColorUtil.Reaction.background
        component.border = if (isHovered) HOVERED_BORDER else BORDER
      })
    }
  }

  private fun showReactionPickerPopup(cs: CoroutineScope, reactionsVm: GitLabReactionsViewModel, component: JComponent) {
    cs.launch {
      GitLabReactionsPickerComponentFactory.showPopup(reactionsVm, component)
    }
  }
}