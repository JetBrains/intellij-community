// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.content

import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ToolWindowInEditorSupport {
  fun canOpenInEditor(project: Project, content: Content): Boolean

  fun openInEditor(content: Content, targetWindow: EditorWindow)
}