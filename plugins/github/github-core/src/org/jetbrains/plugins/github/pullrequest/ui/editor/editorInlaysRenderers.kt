// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.editor.CodeReviewComponentInlayRenderer
import com.intellij.collaboration.ui.codereview.editor.CodeReviewInlayWithOutlineModel
import com.intellij.collaboration.ui.codereview.editor.FadeLayerUI
import com.intellij.collaboration.ui.util.bindContent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.util.addMouseHoverListener
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.hover.HoverStateListener
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.ai.GHPRAICommentViewModel
import org.jetbrains.plugins.github.ai.GHPRAIReviewExtension
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRCompactReviewThreadViewModel
import java.awt.Component
import javax.swing.Icon
import javax.swing.JLayer

@ApiStatus.Internal
class GHPRReviewThreadEditorInlayRenderer internal constructor(
  cs: CoroutineScope,
  hoverableVm: CodeReviewInlayWithOutlineModel,
  vm: GHPRCompactReviewThreadViewModel,
) : CodeReviewComponentInlayRenderer(
  run {
    val fadeLayerUI = FadeLayerUI()
    val layer = JLayer(
      GHPRReviewEditorComponentsFactory.createThreadIn(cs, vm).apply {
        addMouseHoverListener(null, MouseOverInlayListener(hoverableVm))
      }, fadeLayerUI
    )
    layer.launchOnShow("Inlay.Dimming.${vm::javaClass.name}", Dispatchers.EDT) {
      hoverableVm.isDimmed.collect { isDimmed: Boolean ->
        fadeLayerUI.setAlpha(if (isDimmed) 0.5f else 1f, layer)
      }
    }
    layer
  }
)


@ApiStatus.Internal
class GHPRNewCommentEditorInlayRenderer internal constructor(
  cs: CoroutineScope,
  hoverableVm: CodeReviewInlayWithOutlineModel,
  vm: GHPRReviewNewCommentEditorViewModel,
) : CodeReviewComponentInlayRenderer(
  run {
    val fadeLayerUI = FadeLayerUI()
    val layer = JLayer(
      GHPRReviewEditorComponentsFactory.createNewCommentIn(cs, vm).also {
        hoverableVm.showOutline(true)
      }, fadeLayerUI)
    layer.launchOnShow("Inlay.Dimming.${vm::javaClass.name}", Dispatchers.EDT) {
      hoverableVm.isDimmed.collect { isDimmed: Boolean ->
        fadeLayerUI.setAlpha(if (isDimmed) 0.5f else 1f, layer)
      }
    }
    cs.launchNow {
      (hoverableVm as GHPREditorMappedComponentModel.NewComment<*>).isHidden.collect {
        layer.isVisible = !it
      }
    }
    layer
  }
)

internal class GHPRAICommentEditorInlayRenderer internal constructor(userIcon: Icon, vm: GHPRAICommentViewModel)
  : CodeReviewComponentInlayRenderer(Wrapper().apply {
  bindContent("${javaClass.name}.bindContent", GHPRAIReviewExtension.singleFlow) { extension ->
    if (extension == null) return@bindContent null
    extension.createAIThread(userIcon, vm)
  }
})


private class MouseOverInlayListener(val vm: CodeReviewInlayWithOutlineModel) : HoverStateListener() {
  override fun hoverChanged(component: Component, hovered: Boolean) {
    vm.showOutline(hovered)
  }
}
