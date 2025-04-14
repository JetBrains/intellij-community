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
import com.intellij.xdebugger.impl.breakpoints.XBreakpointTypeProxy
import com.intellij.xdebugger.impl.rpc.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
class FrontendXBreakpointProxy(
  override val project: Project,
  private val cs: CoroutineScope,
  private val dto: XBreakpointDto,
  private val onBreakpointChange: () -> Unit,
) : XBreakpointProxy {
  val id: XBreakpointId = dto.id

  override val breakpoint: Any = this

  override val type: XBreakpointTypeProxy = FrontendXBreakpointType(dto.type)

  private val _state: MutableStateFlow<XBreakpointDtoState> = MutableStateFlow(dto.initialState)

  init {
    cs.launch {
      dto.state.toFlow().collectLatest {
        _state.value = it
        onBreakpointChange()
      }
    }
  }

  override fun getDisplayText(): String = _state.value.displayText

  override fun getUserDescription(): String? = _state.value.userDescription

  override fun getGroup(): String? = _state.value.group

  override fun getIcon(): Icon = _state.value.iconId.icon()

  override fun isEnabled(): Boolean = _state.value.enabled

  override fun setEnabled(enabled: Boolean) {
    // TODO: there is a race in changes from server and client,
    //  so we need to merge this state.
    //  Otherwise, multiple clicks on the breakpoint in breakpoint dialog will work in a wrong way.
    _state.update { it.copy(enabled = enabled) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setEnabled(id, enabled)
    }
  }

  override fun getSourcePosition(): XSourcePosition? = _state.value.sourcePosition?.sourcePosition()

  override fun getNavigatable(): Navigatable? = getSourcePosition()?.createNavigatable(project)

  override fun canNavigate(): Boolean = getNavigatable()?.canNavigate() ?: false

  override fun canNavigateToSource(): Boolean = getNavigatable()?.canNavigateToSource() ?: false

  override fun isDefaultBreakpoint(): Boolean = _state.value.isDefault

  override fun getSuspendPolicy(): SuspendPolicy = _state.value.suspendPolicy

  override fun isLogMessage(): Boolean = _state.value.logMessage

  override fun isLogStack(): Boolean = _state.value.logStack

  override fun getLogExpressionObject(): XExpression? = _state.value.logExpressionObject?.xExpression()

  override fun getConditionExpression(): XExpression? = _state.value.conditionExpression?.xExpression()


  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is FrontendXBreakpointProxy) return false

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }
}

@Service(Service.Level.PROJECT)
private class FrontendXBreakpointProjectCoroutineService(val cs: CoroutineScope)