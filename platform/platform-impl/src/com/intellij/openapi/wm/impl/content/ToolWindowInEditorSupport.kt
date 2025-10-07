// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.content

import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.ui.content.Content
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import javax.swing.SwingConstants.*

@ApiStatus.Internal
interface ToolWindowInEditorSupport {
  fun openInEditor(
    content: Content,
    targetWindow: EditorWindow,
    @MagicConstant(intValues = [CENTER.toLong(), TOP.toLong(), LEFT.toLong(), BOTTOM.toLong(), RIGHT.toLong(), -1])
    dropSide: Int,
  )
}