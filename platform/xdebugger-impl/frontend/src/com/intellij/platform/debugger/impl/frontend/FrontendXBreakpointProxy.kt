// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterDraggableObject
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.debugger.impl.frontend.FrontendBreakpointRequestCounter.Companion.REQUEST_IS_NOT_NEEDED
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.platform.util.coroutines.childScope
import com.intellij.pom.Navigatable
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.impl.breakpoints.*
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase.calculateIcon
import com.intellij.xdebugger.impl.rpc.XBreakpointId
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
  private val breakpointRequestCounter: FrontendBreakpointRequestCounter,
  private val _onBreakpointChange: (XBreakpointProxy) -> Unit,
) : XBreakpointProxy {
  override val id: XBreakpointId = dto.id

  protected val cs = parentCs.childScope("FrontendXBreakpointProxy#$id")

  /**
   * Updates should be performed only via [updateStateIfNeeded].
   */
  private val _state: MutableStateFlow<XBreakpointDtoState> = MutableStateFlow(dto.initialState)

  private val editorsProvider = dto.editorsProviderDto?.let {
    getEditorsProvider(cs, it, documentIdProvider = { frontendDocumentId, expression, position, mode ->
      XBreakpointApi.getInstance().createDocument(frontendDocumentId, id, expression, position, mode)
    })
  }

  protected val currentState: XBreakpointDtoState get() = _state.value

  /**
   * Updates breakpoint state if needed.
   * Returns requestId if state was updated, [REQUEST_IS_NOT_NEEDED] otherwise.
   */
  private fun <T> getRequestIdForStateUpdate(
    newValue: T,
    getter: (XBreakpointDtoState) -> T,
    copy: (XBreakpointDtoState) -> XBreakpointDtoState,
  ): Long {
    var requestId: Long = REQUEST_IS_NOT_NEEDED
    _state.update { old ->
      if (getter(old) == newValue) {
        return REQUEST_IS_NOT_NEEDED
      }
      val newState = copy(old)
      if (newState == old) {
        return REQUEST_IS_NOT_NEEDED
      }
      requestId = breakpointRequestCounter.increment()
      newState.copy(requestId = requestId)
    }
    assert(requestId != REQUEST_IS_NOT_NEEDED)
    return requestId
  }

  /**
   * Updates the breakpoint state if needed and sends a request to the backend.
   */
  protected fun <T> updateStateIfNeeded(
    newValue: T,
    getter: (XBreakpointDtoState) -> T,
    copy: (XBreakpointDtoState) -> XBreakpointDtoState,
    afterStateChanged: () -> Unit = {},
    forceRequestWithoutUpdate: Boolean = false,
    sendRequest: suspend (Long) -> Unit,
  ) {
    val requestId = getRequestIdForStateUpdate(newValue, getter, copy)
    if (requestId == REQUEST_IS_NOT_NEEDED && !forceRequestWithoutUpdate) {
      return
    }
    afterStateChanged()
    onBreakpointChange()
    project.service<FrontendXBreakpointProjectCoroutineService>().cs.launch {
      sendRequest(requestId)
    }
  }

  init {
    cs.launch {
      // To avoid races with the backend state updates, we only react to breakpoint state updates
      // which have the latest requestId. Otherwise, we ignore the update.
      dto.state.toFlow().collectLatest {
        if (breakpointRequestCounter.isSuitableUpdate(it.requestId)) {
          _state.value = it
          onBreakpointChange()
        }
      }
    }
  }

  private fun onBreakpointChange() {
    _onBreakpointChange(this)
  }

  override fun getDisplayText(): String = currentState.displayText

  override fun getShortText(): @NlsSafe String {
    return currentState.shortText
  }

  override fun getUserDescription(): String? = currentState.userDescription

  override fun setUserDescription(description: String?) {
    updateStateIfNeeded(newValue = description,
                        getter = { it.userDescription },
                        copy = { it.copy(userDescription = description) }) { requestId ->
      XBreakpointApi.getInstance().setUserDescription(id, requestId, description)
    }
  }

  override fun getGroup(): String? = currentState.group

  override fun setGroup(group: String?) {
    updateStateIfNeeded(newValue = group,
                        getter = { it.group },
                        copy = { it.copy(group = group) }) { requestId ->
      XBreakpointApi.getInstance().setGroup(id, requestId, group)
    }
  }

  override fun getIcon(): Icon {
    // TODO: do we need to cache icon like it is done in XBreakpointBase
    return calculateIcon(this)
  }

  override fun isEnabled(): Boolean = currentState.enabled

  override fun setEnabled(enabled: Boolean) {
    updateStateIfNeeded(newValue = enabled,
                        getter = { it.enabled },
                        copy = { it.copy(enabled = enabled) }) { requestId ->
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
    updateStateIfNeeded(newValue = suspendPolicy,
                        getter = { it.suspendPolicy },
                        copy = { it.copy(suspendPolicy = suspendPolicy) }) { requestId ->
      XBreakpointApi.getInstance().setSuspendPolicy(id, requestId, suspendPolicy)
    }
  }

  override fun getTimestamp(): Long = currentState.timestamp

  override fun isLogMessage(): Boolean = currentState.logMessage
  override fun isLogStack(): Boolean = currentState.logStack
  override fun isLogExpressionEnabled(): Boolean = currentState.isLogExpressionEnabled
  override fun getLogExpressionObjectInt(): XExpression? = currentState.logExpression?.xExpression()

  override fun getLogExpressionObject(): XExpression? {
    return if (isLogExpressionEnabled()) getLogExpressionObjectInt() else null
  }

  override fun setLogMessage(enabled: Boolean) {
    updateStateIfNeeded(newValue = enabled,
                        getter = { it.logMessage },
                        copy = { it.copy(logMessage = enabled) }) { requestId ->
      XBreakpointApi.getInstance().setLogMessage(id, requestId, enabled)
    }
  }

  override fun setLogStack(enabled: Boolean) {
    updateStateIfNeeded(newValue = enabled,
                        getter = { it.logStack },
                        copy = { it.copy(logStack = enabled) }) { requestId ->
      XBreakpointApi.getInstance().setLogStack(id, requestId, enabled)
    }
  }

  override fun setLogExpressionEnabled(enabled: Boolean) {
    updateStateIfNeeded(newValue = enabled,
                        getter = { it.isLogExpressionEnabled },
                        copy = { it.copy(isLogExpressionEnabled = enabled) }) { requestId ->
      XBreakpointApi.getInstance().setLogExpressionEnabled(id, requestId, enabled)
    }
  }

  override fun setLogExpressionObject(logExpression: XExpression?) {
    val logExpressionDto = logExpression?.toRpc()
    updateStateIfNeeded(newValue = logExpressionDto,
                        getter = { it.logExpression },
                        copy = { it.copy(logExpression = logExpressionDto) }) { requestId ->
      XBreakpointApi.getInstance().setLogExpressionObject(id, requestId, logExpressionDto)
    }
  }

  override fun isConditionEnabled(): Boolean = currentState.isConditionEnabled
  override fun getConditionExpressionInt(): XExpression? = currentState.conditionExpression?.xExpression()

  override fun getConditionExpression(): XExpression? {
    return if (isConditionEnabled()) getConditionExpressionInt() else null
  }

  override fun setConditionEnabled(enabled: Boolean) {
    updateStateIfNeeded(newValue = enabled,
                        getter = { it.isConditionEnabled },
                        copy = { it.copy(isConditionEnabled = enabled) }) { requestId ->
      XBreakpointApi.getInstance().setConditionEnabled(id, requestId, enabled)
    }
  }

  override fun setConditionExpression(condition: XExpression?) {
    val conditionDto = condition?.toRpc()
    updateStateIfNeeded(newValue = conditionDto,
                        getter = { it.conditionExpression },
                        copy = { it.copy(conditionExpression = conditionDto) }) { requestId ->
      XBreakpointApi.getInstance().setConditionExpression(id, requestId, conditionDto)
    }
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

    val dependentBreakpointManager = FrontendXDebuggerManager.getInstance(project).breakpointsManager.dependentBreakpointManager
    val otherState = other.currentState

    // A lot of fields in [XBreakpointDtoState] should not be compared
    return currentState.logMessage == otherState.logMessage &&
           currentState.logStack == otherState.logStack &&
           currentState.isLogExpressionEnabled == otherState.isLogExpressionEnabled &&
           currentState.logExpression == otherState.logExpression &&
           currentState.isConditionEnabled == otherState.isConditionEnabled &&
           currentState.conditionExpression == otherState.conditionExpression &&
           currentState.enabled == otherState.enabled &&
           currentState.suspendPolicy == otherState.suspendPolicy &&
           currentState.group == otherState.group &&
           currentState.lineBreakpointInfo == otherState.lineBreakpointInfo &&
           dependentBreakpointManager.getMasterBreakpoint(this) == dependentBreakpointManager.getMasterBreakpoint(other) &&
           dependentBreakpointManager.isLeaveEnabled(this) == dependentBreakpointManager.isLeaveEnabled(other)
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