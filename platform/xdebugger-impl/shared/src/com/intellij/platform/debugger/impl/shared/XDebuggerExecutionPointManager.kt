// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.XSourceKind
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XDebuggerExecutionPointManager {

  var alternativeSourceKindFlow: Flow<Boolean>

  var gutterIconRenderer: GutterIconRenderer?

  fun setExecutionPoint(mainSourcePosition: XSourcePosition?, alternativeSourcePosition: XSourcePosition?, isTopFrame: Boolean, navigationSourceKind: XSourceKind)

  @RequiresEdt
  fun isFullLineHighlighterAt(file: VirtualFile, line: Int, project: Project, isToCheckTopFrameOnly: Boolean): Boolean

  fun clearExecutionPoint()
}