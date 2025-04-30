// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import javax.swing.Icon

internal interface FrontendXLineBreakpointVariant {
  val text: String
  val icon: Icon?
  val highlightRange: TextRange?
  val priority: Int
  fun shouldUseAsInlineVariant(): Boolean
  fun select(res: AsyncPromise<XLineBreakpoint<*>>, temporary: Boolean)
}

internal fun <T : XLineBreakpointType<*>> getFrontendLineBreakpointVariants(
  project: Project,
  types: List<T>,
  position: XSourcePosition,
): Promise<List<FrontendXLineBreakpointVariant>> {
  return XDebuggerUtilImpl.getLineBreakpointVariants(project, types, position).then { variants ->
    variants.map { variant ->
      object : FrontendXLineBreakpointVariant {
        override val text: String = variant.text
        override val icon: Icon? = variant.icon
        override val highlightRange: TextRange? = variant.highlightRange
        override val priority: Int = variant.getPriority(project)
        override fun shouldUseAsInlineVariant(): Boolean = variant.shouldUseAsInlineVariant()
        override fun select(res: AsyncPromise<XLineBreakpoint<*>>, temporary: Boolean) {
          val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
          res.setResult(XDebuggerUtilImpl.addLineBreakpoint(breakpointManager, variant,
                                                            position.file, position.line, temporary))
        }
      }
    }
  }
}
