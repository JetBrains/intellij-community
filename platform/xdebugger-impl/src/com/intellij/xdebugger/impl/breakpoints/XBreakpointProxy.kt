// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.editor.markup.GutterDraggableObject
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.pom.Navigatable
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rpc.XBreakpointId
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
interface XBreakpointProxy : Comparable<XBreakpointProxy> {
  val id: XBreakpointId
  val type: XBreakpointTypeProxy
  val project: Project

  fun getDisplayText(): @NlsSafe String
  fun getShortText(): @NlsSafe String
  fun getUserDescription(): @NlsSafe String?
  fun setUserDescription(description: String?)
  fun getGroup(): String?
  fun setGroup(group: String?)
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

  fun getTimestamp(): Long

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
  fun getTooltipDescription(): @NlsSafe String

  fun haveSameState(other: XBreakpointProxy, ignoreTimestamp: Boolean): Boolean

  fun isLogExpressionEnabled(): Boolean

  fun getLogExpression(): String?
  fun getLogExpressionObjectInt(): XExpression?

  fun setLogMessage(enabled: Boolean)
  fun setLogStack(enabled: Boolean)
  fun setLogExpressionEnabled(enabled: Boolean)
  fun setLogExpressionObject(logExpression: XExpression?)

  fun getEditorsProvider(): XDebuggerEditorsProvider?

  fun getCustomizedPresentation(): CustomizedBreakpointPresentation?

  fun getCustomizedPresentationForCurrentSession(): CustomizedBreakpointPresentation?
  fun isDisposed(): Boolean
  fun updateIcon()

  fun dispose()

  fun createGutterIconRenderer(): GutterIconRenderer?
  fun getGutterIconRenderer(): GutterIconRenderer?

  fun createBreakpointDraggableObject(): GutterDraggableObject?

  open class Monolith @Deprecated("Use breakpoint.asProxy() instead") internal constructor(breakpointBase: XBreakpointBase<*, *, *>) : XBreakpointProxy {
    open val breakpoint: XBreakpointBase<*, *, *> = breakpointBase

    override val id: XBreakpointId get() = breakpoint.breakpointId

    override val type: XBreakpointTypeProxy get() = breakpoint.type.asProxy(breakpoint.project)

    override val project: Project get() = breakpoint.project

    override fun createBreakpointDraggableObject(): GutterDraggableObject? {
      return null
    }

    override fun getDisplayText(): String = XBreakpointUtil.getShortText(breakpoint)
    override fun getShortText(): @NlsSafe String = XBreakpointUtil.getShortText(breakpoint)

    override fun getUserDescription(): String? = breakpoint.userDescription

    override fun setUserDescription(description: String?) {
      breakpoint.userDescription = description
    }

    override fun getGroup(): String? {
      return breakpoint.group
    }

    override fun setGroup(group: String?) {
      breakpoint.group = group
    }

    override fun getIcon(): Icon = breakpoint.getIcon()

    override fun isEnabled(): Boolean = breakpoint.isEnabled

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

    override fun getTimestamp(): Long = breakpoint.timeStamp

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

    override fun getTooltipDescription(): @NlsSafe String {
      return breakpoint.description
    }

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

    override fun getCustomizedPresentation(): CustomizedBreakpointPresentation? {
      return breakpoint.customizedPresentation
    }

    override fun getCustomizedPresentationForCurrentSession(): CustomizedBreakpointPresentation? {
      return (XDebuggerManager.getInstance(project).currentSession as? XDebugSessionImpl)?.getBreakpointPresentation(breakpoint)
    }

    override fun isDisposed(): Boolean = breakpoint.isDisposed
    
    override fun updateIcon() {
      breakpoint.updateIcon()
    }

    override fun dispose() {
      breakpoint.dispose()
    }

    override fun createGutterIconRenderer(): GutterIconRenderer? {
      return breakpoint.createGutterIconRenderer()
    }

    override fun getGutterIconRenderer(): GutterIconRenderer? {
      val lineBreakpoint = breakpoint as? XLineBreakpointImpl<*> ?: return null
      return lineBreakpoint.highlighter?.getGutterIconRenderer()
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Monolith) return false

      if (id != other.id) return false

      return true
    }

    override fun hashCode(): Int {
      return id.hashCode()
    }

    override fun compareTo(other: XBreakpointProxy): Int {
      if (other !is Monolith) {
        return 1
      }

      return compare(breakpoint, other.breakpoint)
    }

    companion object {
      @ApiStatus.Internal
      @Suppress("UNCHECKED_CAST")
      fun <B : XBreakpoint<P>, P : XBreakpointProperties<*>> getEditorsProvider(
        breakpointType: XBreakpointType<B, P>,
        breakpoint: XBreakpoint<*>,
        project: Project,
      ): XDebuggerEditorsProvider? = breakpointType.getEditorsProvider(breakpoint as B, project)

      @Suppress("UNCHECKED_CAST")
      private fun <B : XBreakpoint<P>, P : XBreakpointProperties<*>> compare(
        breakpoint1: XBreakpointBase<B, P, *>,
        breakpoint2: XBreakpoint<*>,
      ): Int = breakpoint1.compareTo(breakpoint2 as B)
    }
  }
}

@Suppress("DEPRECATION")
@ApiStatus.Internal
fun <T : XBreakpointBase<*, *, *>> T.asProxy(): XBreakpointProxy {
  return if (this is XLineBreakpointImpl<*>) {
    this.asProxy()
  }
  else {
    XBreakpointProxy.Monolith(this)
  }
}

@ApiStatus.Internal
fun <T : XLineBreakpointImpl<*>> T.asProxy(): XLineBreakpointProxy = XLineBreakpointProxy.Monolith(this)