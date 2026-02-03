// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared.proxy

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * Light breakpoint is a temporary placeholder for an actual breakpoint.
 * If a breakpoint exists, use [com.intellij.xdebugger.impl.breakpoints.asInlineLightBreakpoint] to wrap it into a light breakpoint.
 * If a breakpoint is not created on the backend yet, this interface can be used for rendering not-yet-existing breakpoint
 * for a smooth RemDev experience.
 */
@ApiStatus.Internal
interface InlineLightBreakpoint {
  val highlightRange: XLineBreakpointHighlighterRange
  val icon: Icon
  val tooltipDescription: String
  val breakpointProxy: XBreakpointProxy?
}

@ApiStatus.Internal
data class InlineVariantWithMatchingBreakpointProxy(
  val variant: XLineBreakpointInlineVariantProxy?,
  val lightBreakpoint: InlineLightBreakpoint?,
) {
  init {
    require(lightBreakpoint != null || variant != null) { "Both breakpoint and variant are null" }
  }
}

@ApiStatus.Internal
interface XLineBreakpointInlineVariantProxy {
  val highlightRange: TextRange?
  val icon: Icon
  val tooltipDescription: String
  fun createBreakpoint(project: Project, file: VirtualFile, document: Document, line: Int)
}
