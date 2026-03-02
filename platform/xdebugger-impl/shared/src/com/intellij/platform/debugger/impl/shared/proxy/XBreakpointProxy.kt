// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared.proxy

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.markup.GutterDraggableObject
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.debugger.impl.rpc.XBreakpointId
import com.intellij.pom.Navigatable
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.impl.breakpoints.CustomizedBreakpointPresentation
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
  @ApiStatus.Internal
  fun supportsInterLinePlacement(): Boolean

  fun getTimestamp(): Long


  fun isLogMessage(): Boolean
  fun isLogStack(): Boolean
  fun isLogExpressionEnabled(): Boolean

  /** Returns the logging expression if it is enabled. */
  fun getLogExpressionObject(): XExpression?

  /** Returns the logging expression even if it is disabled. */
  fun getLogExpressionObjectInt(): XExpression?

  fun setLogMessage(enabled: Boolean)
  fun setLogStack(enabled: Boolean)
  fun setLogExpressionEnabled(enabled: Boolean)
  fun setLogExpressionObject(logExpression: XExpression?)


  fun isConditionEnabled(): Boolean

  /** Returns the condition expression if it is enabled. */
  fun getConditionExpression(): XExpression?

  /** Returns the condition expression even if it is disabled. */
  fun getConditionExpressionInt(): XExpression?

  fun setConditionEnabled(enabled: Boolean)
  fun setConditionExpression(condition: XExpression?)


  @NlsSafe
  fun getGeneralDescription(): String
  fun getTooltipDescription(): @NlsSafe String

  fun haveSameState(other: XBreakpointProxy, ignoreTimestamp: Boolean): Boolean

  fun getEditorsProvider(): XDebuggerEditorsProvider?

  fun getCustomizedPresentation(): CustomizedBreakpointPresentation?

  fun getCustomizedPresentationForCurrentSession(): CustomizedBreakpointPresentation?
  fun isDisposed(): Boolean

  fun dispose()

  fun createGutterIconRenderer(): GutterIconRenderer?
  fun getGutterIconRenderer(): GutterIconRenderer?

  fun createBreakpointDraggableObject(): GutterDraggableObject?

  companion object {
    @JvmField
    val DATA_KEY: DataKey<XBreakpointProxy> = DataKey.create("XBreakpointProxy")
  }
}
