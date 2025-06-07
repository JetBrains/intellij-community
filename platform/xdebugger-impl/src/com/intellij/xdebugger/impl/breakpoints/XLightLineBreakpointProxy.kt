// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XLightLineBreakpointProxy {
  val type: XLineBreakpointTypeProxy

  val project: Project

  fun isDisposed(): Boolean

  fun getFile(): VirtualFile?
  fun getLine(): Int
  fun getHighlightRange(): TextRange?
  fun isEnabled(): Boolean
  fun updateIcon()
  fun createGutterIconRenderer(): GutterIconRenderer?

  @RequiresBackgroundThread
  fun doUpdateUI(callOnUpdate: () -> Unit = {})
}