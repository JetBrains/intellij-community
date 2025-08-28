// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.ui.codereview.editor.CodeReviewComponentInlayRenderer
import com.intellij.collaboration.ui.codereview.editor.CodeReviewInlayModel
import com.intellij.collaboration.util.Hideable
import com.intellij.diff.util.Side
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.plugins.github.ai.GHPRAICommentViewModel
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRCompactReviewThreadViewModel
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRHoverableReviewComment
import javax.swing.Icon

internal sealed interface GHPREditorMappedComponentModel : CodeReviewInlayModel, GHPRHoverableReviewComment {
  val range: StateFlow<Pair<Side, IntRange>?>

  abstract class Thread<VM : GHPRCompactReviewThreadViewModel>(val vm: VM)
    : GHPREditorMappedComponentModel, Hideable {
    final override val key: Any = vm.id
    final override val hiddenState = MutableStateFlow(false)
    final override fun setHidden(hidden: Boolean) {
      hiddenState.value = hidden
    }

    private val _isHovered: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val shouldShowOutline: StateFlow<Boolean> = _isHovered.asStateFlow()

    override fun showOutline(isHovered: Boolean) {
      _isHovered.value = isHovered
    }
  }

  abstract class NewComment<VM : GHPRReviewNewCommentEditorViewModel>(val vm: VM) : GHPREditorMappedComponentModel {
    private val _isHovered: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val shouldShowOutline: StateFlow<Boolean> = _isHovered.asStateFlow()

    override fun showOutline(isHovered: Boolean) {
      _isHovered.value = isHovered
    }
  }

  abstract class AIComment(val vm: GHPRAICommentViewModel)
    : GHPREditorMappedComponentModel, Hideable {
    final override val key: Any = vm.key
    final override val hiddenState = MutableStateFlow(false)
    final override fun setHidden(hidden: Boolean) {
      hiddenState.value = hidden
    }

    private val _isHovered: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val shouldShowOutline: StateFlow<Boolean> = _isHovered.asStateFlow()

    override fun showOutline(isHovered: Boolean) {
      _isHovered.value = isHovered
    }
  }
}

internal fun CoroutineScope.createRenderer(model: GHPREditorMappedComponentModel, userIcon: Icon): CodeReviewComponentInlayRenderer =
  when (model) {
    is GHPREditorMappedComponentModel.Thread<*> -> GHPRReviewThreadEditorInlayRenderer(this, model, model.vm)
    is GHPREditorMappedComponentModel.NewComment<*> -> GHPRNewCommentEditorInlayRenderer(this, model, model.vm)
    is GHPREditorMappedComponentModel.AIComment -> GHPRAICommentEditorInlayRenderer(userIcon, model.vm)
  }
