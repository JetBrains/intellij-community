// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointInlineVariantProxy
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import javax.swing.Icon

private data class MonolithXLineBreakpointInlineVariantProxy(val variant: XLineBreakpointType<*>.XLineBreakpointVariant) : XLineBreakpointInlineVariantProxy {
  override val highlightRange: TextRange?
    get() = variant.highlightRange
  override val icon: Icon
    get() = variant.type.enabledIcon
  override val tooltipDescription: String
    get() = variant.tooltipDescription

  override fun createBreakpoint(project: Project, file: VirtualFile, document: Document, line: Int) {
    val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
    XDebuggerUtilImpl.addLineBreakpoint(breakpointManager, variant, file, line)
  }
}

internal fun XLineBreakpointType<*>.XLineBreakpointVariant.asProxy(): XLineBreakpointInlineVariantProxy =
  MonolithXLineBreakpointInlineVariantProxy(this)
