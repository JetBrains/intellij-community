// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.ui.codereview.editor.CodeReviewActiveRangesTracker
import com.intellij.collaboration.ui.codereview.editor.CodeReviewComponentInlayRenderer
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorInlayRangeOutlineUtils
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorKeys
import com.intellij.collaboration.ui.util.bindContent
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.editor.Editor
import com.intellij.ui.components.panels.Wrapper
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.ai.GHPRAICommentViewModel
import org.jetbrains.plugins.github.ai.GHPRAIReviewExtension
import javax.swing.Icon

@ApiStatus.Internal
class GHPRReviewThreadEditorInlayRenderer internal constructor(
  cs: CoroutineScope,
  editor: Editor,
  model: GHPREditorMappedComponentModel.Thread<*>,
  activeRangesTracker: CodeReviewActiveRangesTracker,
) : CodeReviewComponentInlayRenderer(
  GHPRReviewEditorComponentsFactory.createThreadIn(cs, model.vm).let { threadComponent ->
    val wrapWithDimming = CodeReviewEditorInlayRangeOutlineUtils.wrapWithDimming(threadComponent, model, activeRangesTracker)

    UiDataProvider.wrapComponent(wrapWithDimming) { sink ->
      sink[CodeReviewEditorKeys.NAVIGABLE_EDITOR_KEY] = editor
    }
  }
)

@ApiStatus.Internal
class GHPRNewCommentEditorInlayRenderer internal constructor(
  cs: CoroutineScope,
  editor: Editor,
  model: GHPREditorMappedComponentModel.NewComment<*>,
  activeRangesTracker: CodeReviewActiveRangesTracker,
) : CodeReviewComponentInlayRenderer(
  GHPRReviewEditorComponentsFactory.createNewCommentIn(cs, model.vm).let { newCommentComponent ->
    val wrapWithDimming = CodeReviewEditorInlayRangeOutlineUtils.wrapWithDimming(newCommentComponent, model, activeRangesTracker)
    UiDataProvider.wrapComponent(wrapWithDimming) { sink ->
      sink[CodeReviewEditorKeys.NAVIGABLE_EDITOR_KEY] = editor
    }
  }
)

internal class GHPRAICommentEditorInlayRenderer internal constructor(userIcon: Icon, vm: GHPRAICommentViewModel)
  : CodeReviewComponentInlayRenderer(Wrapper().apply {
  bindContent("${javaClass.name}.bindContent", GHPRAIReviewExtension.singleFlow) { extension ->
    if (extension == null) return@bindContent null
    extension.createAIThread(userIcon, vm)
  }
})

