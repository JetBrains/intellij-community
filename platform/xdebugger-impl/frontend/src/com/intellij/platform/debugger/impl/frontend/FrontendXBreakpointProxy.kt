// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterDraggableObject
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.util.coroutines.childScope
import com.intellij.pom.Navigatable
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.impl.breakpoints.BreakpointGutterIconRenderer
import com.intellij.xdebugger.impl.breakpoints.CustomizedBreakpointPresentation
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase.calculateIcon
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerProxy
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy
import com.intellij.xdebugger.impl.breakpoints.XBreakpointTypeProxy
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointTypeProxy
import com.intellij.xdebugger.impl.rpc.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.swing.Icon

internal fun createXBreakpointProxy(
  project: Project,
  parentCs: CoroutineScope,
  dto: XBreakpointDto,
  type: XBreakpointTypeProxy,
  manager: XBreakpointManagerProxy,
  onBreakpointChange: (XBreakpointProxy) -> Unit,
): XBreakpointProxy {
  return if (type is XLineBreakpointTypeProxy) {
    FrontendXLineBreakpointProxy(project, parentCs, dto, type, manager, onBreakpointChange)
  }
  else {
    FrontendXBreakpointProxy(project, parentCs, dto, type, onBreakpointChange)
  }
}

internal open class FrontendXBreakpointProxy(
  override val project: Project,
  parentCs: CoroutineScope,
  private val dto: XBreakpointDto,
  override val type: XBreakpointTypeProxy,
  private val _onBreakpointChange: (XBreakpointProxy) -> Unit,
) : XBreakpointProxy {
  override val id: XBreakpointId = dto.id

  protected val cs = parentCs.childScope("FrontendXBreakpointProxy#$id")

  protected val _state: MutableStateFlow<XBreakpointDtoState> = MutableStateFlow(dto.initialState)

  private val editorsProvider = dto.localEditorsProvider ?: createFrontendEditorsProvider()

  init {
    cs.launch {
      dto.state.toFlow().collectLatest {
        _state.value = it
        onBreakpointChange()
      }
    }
  }

  protected fun onBreakpointChange() {
    _onBreakpointChange(this)
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
  
  override fun setUserDescription(description: String?) {
    _state.update { it.copy(userDescription = description) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setUserDescription(id, description)
    }
  }

  override fun getGroup(): String? = _state.value.group

  override fun setGroup(group: String?) {
    // TODO IJPL-185322
  }

  override fun getIcon(): Icon {
    // TODO: do we need to cache icon like it is done in XBreakpointBase
    return calculateIcon(this)
  }

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

  override fun getTimestamp(): Long = _state.value.timestamp

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

  override fun getTooltipDescription(): @NlsSafe String {
    return _state.value.tooltipDescription
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

  override fun getCustomizedPresentation(): CustomizedBreakpointPresentation? {
    // TODO: let's convert it once on state change rather then on every getCustomizedPresentation call
    return _state.value.customPresentation?.toPresentation()
  }

  override fun getCustomizedPresentationForCurrentSession(): CustomizedBreakpointPresentation? {
    // TODO: let's convert it once on state change rather then on every getCustomizedPresentation call
    return _state.value.currentSessionCustomPresentation?.toPresentation()
  }

  override fun isDisposed(): Boolean {
    return !cs.isActive
  }

  override fun updateIcon() {
    // TODO IJPL-185322 should we cache icon like in Monolith?
  }

  override fun createGutterIconRenderer(): GutterIconRenderer? {
    return BreakpointGutterIconRenderer(this)
  }

  override fun getGutterIconRenderer(): GutterIconRenderer? {
    // TODO IJPL-185322
    return null
  }

  private fun XBreakpointCustomPresentationDto.toPresentation(): CustomizedBreakpointPresentation {
    val presentation = this
    return CustomizedBreakpointPresentation().apply {
      icon = presentation.icon?.icon()
      errorMessage = presentation.errorMessage
      timestamp = presentation.timestamp
    }
  }

  override fun dispose() {
    cs.cancel()
  }

  override fun createBreakpointDraggableObject(): GutterDraggableObject? {
    return null
  }

  override fun compareTo(other: XBreakpointProxy): Int {
    if (other !is FrontendXBreakpointProxy) {
      return 1
    }
    // TODO: do we need to pass XBreakpointType.getBreakpointComparator somehow?
    //  it always uses timestamps, so it seems like we can keep comparing by timestamps.
    return getTimestamp().compareTo(other.getTimestamp())
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
internal class FrontendXBreakpointProjectCoroutineService(val cs: CoroutineScope)