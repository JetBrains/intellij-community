// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.ui.VisualizedContentTab
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.JComponent

/**
 * Simple tab that displays the optionally [formatted][formatText] text
 * using [fileType]-based highlighting.
 */
@ApiStatus.Experimental // until we consider collection visualizers
abstract class TextBasedContentTab : VisualizedContentTab {
  @VisibleForTesting
  abstract fun formatText(): String

  /** File type of the formatted text. */
  protected abstract val fileType: FileType

  protected fun createEditor(project: Project, parentDisposable: Disposable): Editor =
    DebuggerUIUtil.createFormattedTextViewer(formatText(), fileType, project, parentDisposable)
      .apply { component.border = JBUI.Borders.empty() }

  final override fun createComponent(project: Project, parentDisposable: Disposable): JComponent =
    createEditor(project, parentDisposable).component
}
