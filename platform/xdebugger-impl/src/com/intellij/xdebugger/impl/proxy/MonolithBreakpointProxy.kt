// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.proxy

import com.intellij.openapi.editor.markup.GutterDraggableObject
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.debugger.impl.shared.XBreakpointInterLinePlacementDetector
import com.intellij.platform.debugger.impl.rpc.XBreakpointId
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointTypeProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointTypeProxy
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
import com.intellij.xdebugger.impl.breakpoints.CustomizedBreakpointPresentation
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

internal open class MonolithBreakpointProxy @Deprecated("Use breakpoint.asProxy() instead") internal constructor(breakpointBase: XBreakpointBase<*, *, *>) : XBreakpointProxy {
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

  override fun getSourcePosition(): XSourcePosition? = breakpoint.sourcePosition

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

  override fun supportsInterLinePlacement(): Boolean {
    val lineType = type as? XLineBreakpointTypeProxy ?: return false
    if (!lineType.supportsInterLinePlacement()) return false
    return XBreakpointInterLinePlacementDetector.shouldBePlacedBetweenLines(this)
  }

  override fun getTimestamp(): Long = breakpoint.timeStamp


  override fun isLogMessage(): Boolean = breakpoint.isLogMessage
  override fun isLogStack(): Boolean = breakpoint.isLogStack
  override fun isLogExpressionEnabled(): Boolean = breakpoint.isLogExpressionEnabled
  override fun getLogExpressionObject(): XExpression? = breakpoint.logExpressionObject
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


  override fun isConditionEnabled(): Boolean = breakpoint.isConditionEnabled
  override fun getConditionExpression(): XExpression? = breakpoint.conditionExpression
  override fun getConditionExpressionInt(): XExpression? = breakpoint.conditionExpressionInt

  override fun setConditionEnabled(enabled: Boolean) {
    breakpoint.isConditionEnabled = enabled
  }

  override fun setConditionExpression(condition: XExpression?) {
    breakpoint.conditionExpression = condition
  }


  override fun getGeneralDescription(): String = XBreakpointUtil.getGeneralDescription(breakpoint)

  override fun getTooltipDescription(): @NlsSafe String {
    return breakpoint.description
  }

  override fun haveSameState(other: XBreakpointProxy, ignoreTimestamp: Boolean): Boolean {
    if (other is MonolithBreakpointProxy) {
      return !XBreakpointManagerImpl.statesAreDifferent(breakpoint.state, other.breakpoint.state, ignoreTimestamp)
    }
    return false
  }

  override fun getEditorsProvider(): XDebuggerEditorsProvider? {
    return getEditorsProvider(breakpoint.type, breakpoint, project)
  }

  override fun getCustomizedPresentation(): CustomizedBreakpointPresentation? {
    return breakpoint.customizedPresentation
  }

  override fun getCustomizedPresentationForCurrentSession(): CustomizedBreakpointPresentation? {
    return (XDebuggerManager.getInstance(project).currentSession as? XDebugSessionImpl)?.getBreakpointPresentation(breakpoint)
  }

  override fun isDisposed(): Boolean = breakpoint.isDisposed

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
    if (other !is MonolithBreakpointProxy) return false

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }

  override fun compareTo(other: XBreakpointProxy): Int {
    if (other !is MonolithBreakpointProxy) {
      return 1
    }

    return compare(breakpoint, other.breakpoint)
  }

  companion object {
    @Suppress("UNCHECKED_CAST")
    private fun <B : XBreakpoint<P>, P : XBreakpointProperties<*>> compare(
      breakpoint1: XBreakpointBase<B, P, *>,
      breakpoint2: XBreakpoint<*>,
    ): Int = breakpoint1.compareTo(breakpoint2 as B)
  }
}

@ApiStatus.Internal
@Suppress("UNCHECKED_CAST")
fun <B : XBreakpoint<P>, P : XBreakpointProperties<*>> getEditorsProvider(
  breakpointType: XBreakpointType<B, P>,
  breakpoint: XBreakpoint<*>,
  project: Project,
): XDebuggerEditorsProvider? = breakpointType.getEditorsProvider(breakpoint as B, project)

@Suppress("DEPRECATION")
internal fun <T : XBreakpointBase<*, *, *>> T.asProxy(): XBreakpointProxy {
  return if (this is XLineBreakpointImpl<*>) {
    this.asProxy()
  }
  else {
    MonolithBreakpointProxy(this)
  }
}

@Suppress("DEPRECATION")
internal fun <T : XLineBreakpointImpl<*>> T.asProxy(): XLineBreakpointProxy = MonolithLineBreakpointProxy(this)
