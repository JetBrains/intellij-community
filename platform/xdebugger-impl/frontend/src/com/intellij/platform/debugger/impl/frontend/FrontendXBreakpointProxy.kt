// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy
import com.intellij.xdebugger.impl.rpc.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
class FrontendXBreakpointProxy(
  private val project: Project,
  private val cs: CoroutineScope,
  private val dto: XBreakpointDto,
  private val onEnabledChange: () -> Unit,
) : XBreakpointProxy {
  val id: XBreakpointId = dto.id

  override val breakpoint: Any = id

  private val _enabled = MutableStateFlow(dto.initialEnabled)
  val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

  val suspendPolicy: StateFlow<SuspendPolicy> = dto.suspendPolicyState.toFlow()
    .stateIn(cs, SharingStarted.Eagerly, dto.initialSuspendPolicy)

  val logMessage: StateFlow<Boolean> = dto.logMessageState.toFlow()
    .stateIn(cs, SharingStarted.Eagerly, dto.initialLogMessage)

  val logStack: StateFlow<Boolean> = dto.logStackState.toFlow()
    .stateIn(cs, SharingStarted.Eagerly, dto.initialLogStack)

  val userDescription: StateFlow<String?> = dto.userDescriptionState.toFlow()
    .stateIn(cs, SharingStarted.Eagerly, dto.initialUserDescription)

  init {
    // TODO: there is a race in changes from server and client,
    //  so we need to merge this state.
    //  Otherwise, multiple clicks on the breakpoint in breakpoint dialog will work in a wrong way.
    cs.launch {
      dto.enabledState.toFlow().collectLatest {
        _enabled.value = it
        onEnabledChange()
      }
    }
  }

  override fun getDisplayText(): String = dto.displayText

  override fun getUserDescription(): String? = userDescription.value

  override fun getIcon(): Icon = dto.iconId.icon()

  override fun isEnabled(): Boolean = enabled.value

  override fun setEnabled(enabled: Boolean) {
    _enabled.value = enabled
    onEnabledChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setEnabled(id, enabled)
    }
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

@Service(Service.Level.PROJECT)
private class FrontendXBreakpointProjectCoroutineService(val cs: CoroutineScope)