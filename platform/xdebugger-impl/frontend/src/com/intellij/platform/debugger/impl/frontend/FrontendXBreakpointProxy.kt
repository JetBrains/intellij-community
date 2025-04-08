// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy
import com.intellij.xdebugger.impl.rpc.XBreakpointDto
import com.intellij.xdebugger.impl.rpc.sourcePosition
import com.intellij.xdebugger.impl.rpc.xExpression
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
class FrontendXBreakpointProxy(
  private val project: Project,
  private val cs: CoroutineScope,
  private val dto: XBreakpointDto,
) : XBreakpointProxy {
  val enabled: StateFlow<Boolean> = dto.enabledState.toFlow()
    .stateIn(cs, SharingStarted.Eagerly, dto.initialEnabled)

  val suspendPolicy: StateFlow<SuspendPolicy> = dto.suspendPolicyState.toFlow()
    .stateIn(cs, SharingStarted.Eagerly, dto.initialSuspendPolicy)

  val logMessage: StateFlow<Boolean> = dto.logMessageState.toFlow()
    .stateIn(cs, SharingStarted.Eagerly, dto.initialLogMessage)

  val logStack: StateFlow<Boolean> = dto.logStackState.toFlow()
    .stateIn(cs, SharingStarted.Eagerly, dto.initialLogStack)

  val userDescription: StateFlow<String?> = dto.userDescriptionState.toFlow()
    .stateIn(cs, SharingStarted.Eagerly, dto.initialUserDescription)

  override fun getDisplayText(): String = dto.displayText

  override fun getUserDescription(): String? = userDescription.value

  override fun getIcon(): Icon = dto.iconId.icon()

  override fun isEnabled(): Boolean = enabled.value

  override fun setEnabled(enabled: Boolean) {
    // TODO: implement through RPC
  }

  override fun getSourcePosition(): XSourcePosition? = dto.sourcePosition?.sourcePosition()

  override fun getNavigatable(): Navigatable? = getSourcePosition()?.createNavigatable(project)

  override fun canNavigate(): Boolean = getNavigatable()?.canNavigate() ?: false

  override fun canNavigateToSource(): Boolean = getNavigatable()?.canNavigateToSource() ?: false

  override fun isDefaultBreakpoint(): Boolean = dto.isDefault

  override fun getSuspendPolicy(): SuspendPolicy = suspendPolicy.value

  override fun isLogMessage(): Boolean = logMessage.value

  override fun isLogStack(): Boolean = logStack.value

  override fun getLogExpressionObject(): XExpression? = dto.logExpressionObject?.xExpression()

  override fun getConditionExpression(): XExpression? = dto.conditionExpression?.xExpression()
}