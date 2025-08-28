// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.ui.codereview.editor.CodeReviewComponentInlayRenderer
import com.intellij.collaboration.ui.util.bindContent
import com.intellij.openapi.observable.util.addMouseHoverListener
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.hover.HoverStateListener
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.ai.GHPRAICommentViewModel
import org.jetbrains.plugins.github.ai.GHPRAIReviewExtension
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRCompactReviewThreadViewModel
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRHoverableReviewComment
import java.awt.Component
import javax.swing.Icon

@ApiStatus.Internal
class GHPRReviewThreadEditorInlayRenderer internal constructor(
  cs: CoroutineScope,
  hoverableVm: GHPRHoverableReviewComment,
  vm: GHPRCompactReviewThreadViewModel,
) : CodeReviewComponentInlayRenderer(
  GHPRReviewEditorComponentsFactory.createThreadIn(cs, vm).apply {
    addMouseHoverListener(null, MouseOverInlayListener(hoverableVm))
  }
)


@ApiStatus.Internal
class GHPRNewCommentEditorInlayRenderer internal constructor(
  cs: CoroutineScope,
  hoverableVm: GHPRHoverableReviewComment,
  vm: GHPRReviewNewCommentEditorViewModel,
) : CodeReviewComponentInlayRenderer(
  GHPRReviewEditorComponentsFactory.createNewCommentIn(cs, vm).also {
    hoverableVm.showOutline(true)
  }
)

internal class GHPRAICommentEditorInlayRenderer internal constructor(userIcon: Icon, vm: GHPRAICommentViewModel)
  : CodeReviewComponentInlayRenderer(Wrapper().apply {
  bindContent("${javaClass.name}.bindContent", GHPRAIReviewExtension.singleFlow) { extension ->
    if (extension == null) return@bindContent null
    extension.createAIThread(userIcon, vm)
  }
})

private class MouseOverInlayListener(val vm: GHPRHoverableReviewComment) : HoverStateListener() {
  override fun hoverChanged(component: Component, hovered: Boolean) {
    vm.showOutline(hovered)
  }
}