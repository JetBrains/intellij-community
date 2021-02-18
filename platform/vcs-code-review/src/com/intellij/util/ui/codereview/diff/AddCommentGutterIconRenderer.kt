// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.codereview.diff

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.codereview.messages.VcsCodeReviewBundle
import javax.swing.Icon

abstract class AddCommentGutterIconRenderer : GutterIconRenderer(), DumbAware, Disposable {

  abstract val line: Int

  var iconVisible = false

  override fun getIcon(): Icon = if (iconVisible) AllIcons.General.InlineAdd else EmptyIcon.ICON_16

  override fun getTooltipText() = VcsCodeReviewBundle.message("diff.add.comment.icon.tooltip")

  override fun isNavigateAction() = true

  override fun getAlignment() = Alignment.RIGHT

  abstract fun disposeInlay()

  override fun dispose() {
    disposeInlay()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AddCommentGutterIconRenderer) return false

    if (line != other.line) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + line
    return result
  }
}