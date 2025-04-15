// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.pom.Navigatable
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
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

  override val type: XBreakpointTypeProxy = FrontendXBreakpointType(project, dto.type)

  private val _state: MutableStateFlow<XBreakpointDtoState> = MutableStateFlow(dto.initialState)

  private val editorsProvider = dto.localEditorsProvider ?: createFrontendEditorsProvider()

  init {
    cs.launch {
      dto.state.toFlow().collectLatest {
        _state.value = it
        onBreakpointChange()
      }
    }
  }

  private fun createFrontendEditorsProvider(): FrontendXDebuggerEditorsProvider? {
    val fileTypeId = dto.editorsProviderFileTypeId ?: return null
    return FrontendXDebuggerEditorsProvider(fileTypeId) { frontendDocumentId, expression, position, mode ->
      XBreakpointApi.getInstance().createDocument(frontendDocumentId, id, expression, position, mode)
    }
  }

  override fun getDisplayText(): String = _state.value.displayText

  override fun getShortText(): @NlsSafe String {
    return _state.value.shortText
  }

  internal fun currentState(): XBreakpointDtoState {
    return _state.value
  }

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

  override fun setSuspendPolicy(suspendPolicy: SuspendPolicy) {
    // TODO: there is a race in changes from server and client,
    //  so we need to merge this state.
    //  Otherwise, multiple clicks on the breakpoint in breakpoint dialog will work in a wrong way.
    _state.update { it.copy(suspendPolicy = suspendPolicy) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setSuspendPolicy(id, suspendPolicy)
    }
  }

  override fun isLogMessage(): Boolean = _state.value.logMessage

  override fun isLogStack(): Boolean = _state.value.logStack

  override fun isConditionEnabled(): Boolean {
    return _state.value.isConditionEnabled
  }

  override fun setConditionEnabled(enabled: Boolean) {
    _state.update { it.copy(isConditionEnabled = enabled) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setConditionEnabled(id, enabled)
    }
  }

  override fun getLogExpressionObject(): XExpression? = _state.value.logExpressionObject?.xExpression()

  override fun getConditionExpression(): XExpression? = _state.value.conditionExpression?.xExpression()

  override fun setConditionExpression(condition: XExpression?) {
    val conditionDto = condition?.toRpc()
    _state.update { it.copy(conditionExpression = conditionDto) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setConditionExpression(id, conditionDto)
    }
  }

  override fun getConditionExpressionInt(): XExpression? {
    return _state.value.conditionExpressionInt?.xExpression()
  }

  override fun getGeneralDescription(): String {
    return _state.value.generalDescription
  }

  override fun haveSameState(other: XBreakpointProxy, ignoreTimestamp: Boolean): Boolean {
    if (other !is FrontendXBreakpointProxy) {
      return false
    }

    // TODO: support timestamp
    return currentState() == other.currentState()
  }

  override fun isLogExpressionEnabled(): Boolean {
    return _state.value.isLogExpressionEnabled
  }

  override fun getLogExpression(): String? {
    return _state.value.logExpression
  }

  override fun getLogExpressionObjectInt(): XExpression? {
    return _state.value.logExpressionObjectInt?.xExpression()
  }

  override fun setLogMessage(enabled: Boolean) {
    _state.update { it.copy(logMessage = enabled) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setLogMessage(id, enabled)
    }
  }

  override fun setLogStack(enabled: Boolean) {
    _state.update { it.copy(logStack = enabled) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setLogStack(id, enabled)
    }
  }

  override fun setLogExpressionEnabled(enabled: Boolean) {
    _state.update { it.copy(isLogExpressionEnabled = enabled) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setLogExpressionEnabled(id, enabled)
    }
  }

  override fun setLogExpressionObject(logExpression: XExpression?) {
    val logExpressionDto = logExpression?.toRpc()
    _state.update { it.copy(logExpressionObject = logExpressionDto) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setLogExpressionObject(id, logExpressionDto)
    }
  }

  override fun getEditorsProvider(): XDebuggerEditorsProvider? {
    return editorsProvider
  }

  override fun isTemporary(): Boolean {
    return _state.value.isTemporary
  }

  override fun setTemporary(isTemporary: Boolean) {
    _state.update { it.copy(isTemporary = isTemporary) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setTemporary(id, isTemporary)
    }
  }


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