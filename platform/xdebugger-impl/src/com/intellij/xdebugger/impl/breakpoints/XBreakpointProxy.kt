// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.pom.Navigatable
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
interface XBreakpointProxy {
  val breakpoint: Any
  val type: XBreakpointTypeProxy
  val project: Project

  fun getDisplayText(): @NlsSafe String
  fun getUserDescription(): @NlsSafe String?
  fun getGroup(): String?
  fun getIcon(): Icon
  fun isEnabled(): Boolean
  fun setEnabled(enabled: Boolean)
  fun getSourcePosition(): XSourcePosition?
  fun getNavigatable(): Navigatable?
  fun canNavigate(): Boolean
  fun canNavigateToSource(): Boolean
  fun isDefaultBreakpoint(): Boolean
  fun getSuspendPolicy(): SuspendPolicy
  fun isLogMessage(): Boolean
  fun isLogStack(): Boolean
  fun getLogExpressionObject(): XExpression?
  fun getConditionExpression(): XExpression?

  class Monolith(override val breakpoint: XBreakpointBase<*, *, *>) : XBreakpointProxy {
    override val type: XBreakpointTypeProxy = XBreakpointTypeProxy.Monolith(breakpoint.getType())

    override val project: Project = breakpoint.project

    override fun getDisplayText(): String = XBreakpointUtil.getShortText(breakpoint)

    override fun getUserDescription(): String? = breakpoint.userDescription

    override fun getGroup(): String? {
      return breakpoint.group
    }

    override fun getIcon(): Icon = breakpoint.getIcon()

    override fun isEnabled(): Boolean = breakpoint.isEnabled()

    override fun setEnabled(enabled: Boolean) {
      breakpoint.setEnabled(enabled)
    }

    override fun getSourcePosition(): XSourcePosition? = breakpoint.getSourcePosition()

    override fun getNavigatable(): Navigatable? = breakpoint.getNavigatable()

    override fun canNavigate(): Boolean = breakpoint.getNavigatable()?.canNavigate() ?: false

    override fun canNavigateToSource(): Boolean = breakpoint.getNavigatable()?.canNavigateToSource() ?: false

    override fun isDefaultBreakpoint(): Boolean {
      val breakpointManager = breakpoint.breakpointManager
      return breakpointManager.isDefaultBreakpoint(breakpoint)
    }

    override fun getSuspendPolicy(): SuspendPolicy = breakpoint.getSuspendPolicy()

    override fun isLogMessage(): Boolean = breakpoint.isLogMessage()

    override fun isLogStack(): Boolean = breakpoint.isLogStack()

    override fun getLogExpressionObject(): XExpression? = breakpoint.getLogExpressionObject()

    override fun getConditionExpression(): XExpression? = breakpoint.getConditionExpression()
  }
}