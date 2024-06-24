// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.ui.VisualizedContentTab
import javax.swing.JComponent

/**
 * Simple tab that displays the optionally [formatted][formatText] text
 * using [fileType]-based highlighting.
 */
abstract class TextBasedContentTab : VisualizedContentTab {
  protected abstract fun formatText(): String

  protected abstract val fileType: FileType

  override fun createComponent(project: Project): JComponent =
    DebuggerUIUtil.createTextViewer(formatText(), project, fileType)
      .also { it.border = JBUI.Borders.customLine(it.background, 10) }
}
