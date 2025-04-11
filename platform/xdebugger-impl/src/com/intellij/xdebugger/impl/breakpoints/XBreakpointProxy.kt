// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.pom.Navigatable
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
interface XBreakpointProxy {
  val breakpoint: Any
  val type: XBreakpointTypeProxy
  val project: Project

  fun getDisplayText(): @NlsSafe String
  fun getShortText(): @NlsSafe String
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
  fun setSuspendPolicy(suspendPolicy: SuspendPolicy)

  fun isLogMessage(): Boolean
  fun isLogStack(): Boolean

  fun isConditionEnabled(): Boolean
  fun setConditionEnabled(enabled: Boolean)

  fun getLogExpressionObject(): XExpression?

  fun getConditionExpression(): XExpression?
  fun setConditionExpression(condition: XExpression?)

  fun getConditionExpressionInt(): XExpression?

  @NlsSafe
  fun getGeneralDescription(): String

  fun haveSameState(other: XBreakpointProxy, ignoreTimestamp: Boolean): Boolean

  fun isLogExpressionEnabled(): Boolean

  fun getLogExpression(): String?
  fun getLogExpressionObjectInt(): XExpression?

  fun setLogMessage(enabled: Boolean)
  fun setLogStack(enabled: Boolean)
  fun setLogExpressionEnabled(enabled: Boolean)
  fun setLogExpressionObject(logExpression: XExpression?)

  fun getEditorsProvider(): XDebuggerEditorsProvider?

  fun isTemporary(): Boolean

  // Supported only for line breakpoints
  fun setTemporary(isTemporary: Boolean)

  class Monolith(override val breakpoint: XBreakpointBase<*, *, *>) : XBreakpointProxy {
    override val type: XBreakpointTypeProxy = XBreakpointTypeProxy.Monolith(breakpoint.project, breakpoint.getType())

    override val project: Project = breakpoint.project

    override fun getDisplayText(): String = XBreakpointUtil.getShortText(breakpoint)
    override fun getShortText(): @NlsSafe String = XBreakpointUtil.getShortText(breakpoint)

    override fun getUserDescription(): String? = breakpoint.userDescription

    override fun getGroup(): String? {
      return breakpoint.group
    }

    override fun getIcon(): Icon = breakpoint.getIcon()

    override fun isEnabled(): Boolean = breakpoint.isEnabled()

    override fun setEnabled(enabled: Boolean) {
      breakpoint.isEnabled = enabled
    }

    override fun getSourcePosition(): XSourcePosition? = breakpoint.getSourcePosition()

    override fun getNavigatable(): Navigatable? = breakpoint.getNavigatable()

    override fun canNavigate(): Boolean = breakpoint.getNavigatable()?.canNavigate() ?: false

    override fun canNavigateToSource(): Boolean = breakpoint.getNavigatable()?.canNavigateToSource() ?: false

    override fun isDefaultBreakpoint(): Boolean {
      val breakpointManager = breakpoint.breakpointManager
      return breakpointManager.isDefaultBreakpoint(breakpoint)
    }

    override fun getSuspendPolicy(): SuspendPolicy = breakpoint.suspendPolicy

    override fun setSuspendPolicy(suspendPolicy: SuspendPolicy) {
      breakpoint.suspendPolicy = suspendPolicy
    }

    override fun isLogMessage(): Boolean = breakpoint.isLogMessage

    override fun isLogStack(): Boolean = breakpoint.isLogStack
    override fun isConditionEnabled(): Boolean = breakpoint.isConditionEnabled

    override fun setConditionEnabled(enabled: Boolean) {
      breakpoint.isConditionEnabled = enabled
    }

    override fun getLogExpressionObject(): XExpression? = breakpoint.logExpressionObject

    override fun getConditionExpression(): XExpression? = breakpoint.conditionExpression
    override fun setConditionExpression(condition: XExpression?) {
      breakpoint.conditionExpression = condition
    }

    override fun getConditionExpressionInt(): XExpression? = breakpoint.conditionExpressionInt

    override fun getGeneralDescription(): String = XBreakpointUtil.getGeneralDescription(breakpoint)

    override fun haveSameState(other: XBreakpointProxy, ignoreTimestamp: Boolean): Boolean {
      if (other is Monolith) {
        return XBreakpointManagerImpl.statesAreDifferent(breakpoint.state, other.breakpoint.state, ignoreTimestamp)
      }
      return false
    }

    override fun getEditorsProvider(): XDebuggerEditorsProvider? {
      return getEditorsProvider(breakpoint.type, breakpoint, project)
    }

    override fun isLogExpressionEnabled(): Boolean = breakpoint.isLogExpressionEnabled

    override fun getLogExpression(): String? = breakpoint.logExpression

    override fun getLogExpressionObjectInt(): XExpression? = breakpoint.logExpressionObjectInt

    override fun setLogMessage(enabled: Boolean) {
      breakpoint.isLogMessage = enabled
    }

    override fun setLogStack(enabled: Boolean) {
      breakpoint.isLogStack = enabled
    }

    override fun setLogExpressionEnabled(enabled: Boolean) {
      breakpoint.isLogExpressionEnabled = enabled
    }

    override fun setLogExpressionObject(logExpression: XExpression?) {
      breakpoint.logExpressionObject = logExpression
    }

    override fun isTemporary(): Boolean = (breakpoint as? XLineBreakpoint<*>)?.isTemporary ?: false

    override fun setTemporary(isTemporary: Boolean) {
      if (breakpoint is XLineBreakpoint<*>) {
        breakpoint.isTemporary = isTemporary
      }
    }

    companion object {
      @ApiStatus.Internal
      @Suppress("UNCHECKED_CAST")
      fun <B : XBreakpoint<P>, P : XBreakpointProperties<*>> getEditorsProvider(
        breakpointType: XBreakpointType<B, P>,
        breakpoint: XBreakpoint<*>,
        project: Project,
      ): XDebuggerEditorsProvider? = breakpointType.getEditorsProvider(breakpoint as B, project)
    }
  }
}