// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import javax.swing.Icon

internal interface XLineBreakpointInlineVariantProxy {
  val highlightRange: TextRange?
  val icon: Icon
  val tooltipDescription: String
  fun isMatching(breakpoint: XLineBreakpointProxy): Boolean
  fun createBreakpoint(project: Project, file: VirtualFile, line: Int)

  data class Monolith(val variant: XLineBreakpointType<*>.XLineBreakpointVariant) : XLineBreakpointInlineVariantProxy {
    override val highlightRange: TextRange?
      get() = variant.highlightRange
    override val icon: Icon
      get() = variant.type.enabledIcon
    override val tooltipDescription: String
      get() = variant.tooltipDescription

    @Suppress("UNCHECKED_CAST") // Casts are required for gods of Kotlin-Java type inference.
    override fun isMatching(breakpoint: XLineBreakpointProxy): Boolean {
      val type = variant.type as XLineBreakpointType<XBreakpointProperties<*>>
      val b = (breakpoint as? XLineBreakpointProxy.Monolith)?.breakpoint as? XLineBreakpointImpl<XBreakpointProperties<*>>
      if (b == null) return false
      val v = variant as XLineBreakpointType<XBreakpointProperties<*>>.XLineBreakpointVariant

      return type == variant.type && type.variantAndBreakpointMatch(b, v)
    }

    override fun createBreakpoint(project: Project, file: VirtualFile, line: Int) {
      val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
      XDebuggerUtilImpl.addLineBreakpoint(breakpointManager, variant, file, line)
    }
  }
}

internal fun XLineBreakpointType<*>.XLineBreakpointVariant.asProxy(): XLineBreakpointInlineVariantProxy.Monolith =
  XLineBreakpointInlineVariantProxy.Monolith(this)
