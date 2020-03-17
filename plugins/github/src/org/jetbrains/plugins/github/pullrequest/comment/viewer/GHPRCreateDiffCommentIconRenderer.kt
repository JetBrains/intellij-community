// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.viewer

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import javax.swing.Icon

class GHPRCreateDiffCommentIconRenderer(val line: Int, private val action: DumbAwareAction) : GutterIconRenderer(), DumbAware {
  override fun getClickAction() = action
  override fun isNavigateAction() = true
  override fun getIcon(): Icon = AllIcons.General.InlineAdd
  override fun equals(other: Any?): Boolean = other is GHPRCreateDiffCommentIconRenderer && line == other.line
  override fun hashCode(): Int = line.hashCode()
}