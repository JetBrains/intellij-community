// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterDraggableObject
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.debugger.impl.rpc.XBreakpointApi
import com.intellij.platform.util.coroutines.childScope
import com.intellij.pom.Navigatable
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.impl.breakpoints.*
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase.calculateIcon
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
  manager: FrontendXBreakpointManager,
  onBreakpointChange: (XBreakpointProxy) -> Unit,
): XBreakpointProxy {
  return if (type is XLineBreakpointTypeProxy) {
    FrontendXLineBreakpointProxy(project, parentCs, dto, type, manager, onBreakpointChange)
  }
  else {
    FrontendXBreakpointProxy(project, parentCs, dto, type, manager.breakpointRequestCounter, onBreakpointChange)
  }
}

internal open class FrontendXBreakpointProxy(
  override val project: Project,
  parentCs: CoroutineScope,
  private val dto: XBreakpointDto,
  override val type: XBreakpointTypeProxy,
  private val breakpointRequestCounter: BreakpointRequestCounter,
  private val _onBreakpointChange: (XBreakpointProxy) -> Unit,
) : XBreakpointProxy {
  override val id: XBreakpointId = dto.id

  protected val cs = parentCs.childScope("FrontendXBreakpointProxy#$id")

  private val _state: MutableStateFlow<XBreakpointDtoState> = MutableStateFlow(dto.initialState)

  private val editorsProvider = dto.localEditorsProvider ?: createFrontendEditorsProvider()

  protected val currentState: XBreakpointDtoState get() = _state.value

  protected inline fun updateState(update: (XBreakpointDtoState) -> XBreakpointDtoState): Long {
    var requestId: Long = -1
    _state.update {
      requestId = breakpointRequestCounter.increment()
      update(it).copy(requestId = requestId)
    }
    assert(requestId != -1L)
    return requestId
  }

  init {
    cs.launch {
      dto.state.toFlow().collectLatest {
        if (breakpointRequestCounter.isSuitableUpdate(it.requestId)) {
          _state.value = it
          onBreakpointChange()
        }
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

  override fun getDisplayText(): String = currentState.displayText

  override fun getShortText(): @NlsSafe String {
    return currentState.shortText
  }

  override fun getUserDescription(): String? = currentState.userDescription

  override fun setUserDescription(description: String?) {
    val requestId = updateState { it.copy(userDescription = description) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setUserDescription(id, requestId, description)
    }
  }

  override fun getGroup(): String? = currentState.group

  override fun setGroup(group: String?) {
    // TODO IJPL-185322
  }

  override fun getIcon(): Icon {
    // TODO: do we need to cache icon like it is done in XBreakpointBase
    return calculateIcon(this)
  }

  override fun isEnabled(): Boolean = currentState.enabled

  override fun setEnabled(enabled: Boolean) {
    val requestId = updateState { it.copy(enabled = enabled) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setEnabled(id, requestId, enabled)
    }
  }

  override fun getSourcePosition(): XSourcePosition? = currentState.sourcePosition?.sourcePosition()

  override fun getNavigatable(): Navigatable? = getSourcePosition()?.createNavigatable(project)

  override fun canNavigate(): Boolean = getNavigatable()?.canNavigate() ?: false

  override fun canNavigateToSource(): Boolean = getNavigatable()?.canNavigateToSource() ?: false

  override fun isDefaultBreakpoint(): Boolean = currentState.isDefault

  override fun getSuspendPolicy(): SuspendPolicy = currentState.suspendPolicy

  override fun setSuspendPolicy(suspendPolicy: SuspendPolicy) {
    val requestId = updateState { it.copy(suspendPolicy = suspendPolicy) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setSuspendPolicy(id, requestId, suspendPolicy)
    }
  }

  override fun getTimestamp(): Long = currentState.timestamp

  override fun isLogMessage(): Boolean = currentState.logMessage

  override fun isLogStack(): Boolean = currentState.logStack

  override fun isConditionEnabled(): Boolean {
    return currentState.isConditionEnabled
  }

  override fun setConditionEnabled(enabled: Boolean) {
    val requestId = updateState { it.copy(isConditionEnabled = enabled) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setConditionEnabled(id, requestId, enabled)
    }
  }

  override fun getLogExpressionObject(): XExpression? = currentState.logExpressionObject?.xExpression()

  override fun getConditionExpression(): XExpression? = currentState.conditionExpression?.xExpression()

  override fun setConditionExpression(condition: XExpression?) {
    val conditionDto = condition?.toRpc()
    val requestId = updateState { it.copy(conditionExpression = conditionDto) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setConditionExpression(id, requestId, conditionDto)
    }
  }

  override fun getConditionExpressionInt(): XExpression? {
    return currentState.conditionExpressionInt?.xExpression()
  }

  override fun getGeneralDescription(): String {
    return currentState.generalDescription
  }

  override fun getTooltipDescription(): @NlsSafe String {
    return currentState.tooltipDescription
  }

  override fun haveSameState(other: XBreakpointProxy, ignoreTimestamp: Boolean): Boolean {
    if (other !is FrontendXBreakpointProxy) {
      return false
    }

    // TODO: support timestamp
    return currentState == other.currentState
  }

  override fun isLogExpressionEnabled(): Boolean {
    return currentState.isLogExpressionEnabled
  }

  override fun getLogExpression(): String? {
    return currentState.logExpression
  }

  override fun getLogExpressionObjectInt(): XExpression? {
    return currentState.logExpressionObjectInt?.xExpression()
  }

  override fun setLogMessage(enabled: Boolean) {
    val requestId = updateState { it.copy(logMessage = enabled) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setLogMessage(id, requestId, enabled)
    }
  }

  override fun setLogStack(enabled: Boolean) {
    val requestId = updateState { it.copy(logStack = enabled) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setLogStack(id, requestId, enabled)
    }
  }

  override fun setLogExpressionEnabled(enabled: Boolean) {
    val requestId = updateState { it.copy(isLogExpressionEnabled = enabled) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setLogExpressionEnabled(id, requestId, enabled)
    }
  }

  override fun setLogExpressionObject(logExpression: XExpression?) {
    val logExpressionDto = logExpression?.toRpc()
    val requestId = updateState { it.copy(logExpressionObject = logExpressionDto) }
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      XBreakpointApi.getInstance().setLogExpressionObject(id, requestId, logExpressionDto)
    }
  }

  override fun getEditorsProvider(): XDebuggerEditorsProvider? {
    return editorsProvider
  }

  override fun getCustomizedPresentation(): CustomizedBreakpointPresentation? {
    // TODO: let's convert it once on state change rather then on every getCustomizedPresentation call
    return currentState.customPresentation?.toPresentation()
  }

  override fun getCustomizedPresentationForCurrentSession(): CustomizedBreakpointPresentation? {
    // TODO: let's convert it once on state change rather then on every getCustomizedPresentation call
    return currentState.currentSessionCustomPresentation?.toPresentation()
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

  override fun toString(): String {
    return this::class.simpleName + "(id=$id, type=${type.id})"
  }
}

@Service(Service.Level.PROJECT)
internal class FrontendXBreakpointProjectCoroutineService(val cs: CoroutineScope)