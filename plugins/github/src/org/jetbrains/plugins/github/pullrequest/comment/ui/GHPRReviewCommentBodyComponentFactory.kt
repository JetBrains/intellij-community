// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.ComponentListPanelFactory.createVertical
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.setHtmlBody
import kotlinx.coroutines.CoroutineScope
import javax.swing.JComponent

internal object GHPRReviewCommentBodyComponentFactory {
  fun createIn(cs: CoroutineScope, vm: GHPRReviewCommentBodyViewModel, maxTextWidth: Int): JComponent =
    createVertical(cs, vm.blocks) { block ->
      when (block) {
        is GHPRCommentBodyBlock.HTML -> SimpleHtmlPane(customImageLoader = vm.htmlImageLoader).apply {
          setHtmlBody(block.body)
        }.let {
          CollaborationToolsUIUtil.wrapWithLimitedSize(it, maxWidth = maxTextWidth)
        }
        is GHPRCommentBodyBlock.SuggestedChange -> {
          GHPRReviewSuggestedChangeComponentFactory2.createIn(this, vm, block)
        }
      }
    }
}
