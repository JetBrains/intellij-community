// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.rpc.BackendDocumentId
import com.intellij.ide.rpc.FrontendDocumentId
import com.intellij.ide.rpc.bindToFrontend
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ThreeState
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XSteppingSuspendContext
import com.intellij.xdebugger.impl.frame.ColorState
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList
import com.intellij.xdebugger.impl.rpc.*
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.rpc.models.getOrStoreGlobally
import com.intellij.xdebugger.impl.rpc.models.storeGlobally
import com.intellij.xdebugger.stepping.ForceSmartStepIntoSource
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant
import fleet.rpc.core.toRpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.await
import javax.swing.Icon

internal class BackendXDebugSessionApi : XDebugSessionApi {
  override suspend fun currentSourcePosition(sessionId: XDebugSessionId): Flow<XSourcePositionDto?> {
    val session = sessionId.findValue() ?: return emptyFlow()
    return session.getCurrentPositionFlow().map { sourcePosition ->
      sourcePosition?.toRpc()
    }
  }

  override suspend fun topSourcePosition(sessionId: XDebugSessionId): Flow<XSourcePositionDto?> {
    val session = sessionId.findValue() ?: return emptyFlow()
    return session.topFrameFlow.map {
      session.topFramePosition?.toRpc()
    }
  }

  override suspend fun currentSessionState(sessionId: XDebugSessionId): Flow<XDebugSessionState> {
    val session = sessionId.findValue() ?: return emptyFlow()

    return combine(
      session.isPausedState, session.isStoppedState, session.isReadOnlyState, session.isPauseActionSupportedState, session.isSuspendedState
    ) { isPaused, isStopped, isReadOnly, isPauseActionSupported, isSuspended ->
      XDebugSessionState(isPaused, isStopped, isReadOnly, isPauseActionSupported, isSuspended)
    }
  }

  override suspend fun createDocument(frontendDocumentId: FrontendDocumentId, sessionId: XDebugSessionId, expression: XExpressionDto, sourcePosition: XSourcePositionDto?, evaluationMode: EvaluationMode): BackendDocumentId? {
    val session = sessionId.findValue() ?: return null
    val project = session.project
    val editorsProvider = session.debugProcess.editorsProvider
    return withContext(Dispatchers.EDT) {
      val backendDocument = editorsProvider.createDocument(project, expression.xExpression(), sourcePosition?.sourcePosition(), evaluationMode)
      backendDocument.bindToFrontend(frontendDocumentId)
    }
  }

  override suspend fun sessionTabInfo(sessionId: XDebugSessionId): Flow<XDebuggerSessionTabDto?> {
    val session = sessionId.findValue() ?: return emptyFlow()
    return session.tabInitDataFlow.map {
      if (it == null) return@map null
      XDebuggerSessionTabDto(it, session.getPausedEventsFlow().toRpc())
    }
  }

  override suspend fun resume(sessionId: XDebugSessionId) {
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.resume()
    }
  }

  override suspend fun pause(sessionId: XDebugSessionId) {
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.pause()
    }
  }

  override suspend fun stepOver(sessionId: XDebugSessionId, ignoreBreakpoints: Boolean) {
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.stepOver(ignoreBreakpoints)
    }
  }

  override suspend fun stepOut(sessionId: XDebugSessionId) {
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.stepOut()
    }
  }

  override suspend fun stepInto(sessionId: XDebugSessionId) {
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.stepInto()
    }
  }

  override suspend fun smartStepIntoEmpty(sessionId: XDebugSessionId) {
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.debugProcess.smartStepIntoHandler?.stepIntoEmpty(session)
    }
  }

  override suspend fun smartStepInto(smartStepTargetId: XSmartStepIntoTargetId) {
    val targetModel = smartStepTargetId.findValue() ?: return
    val session = targetModel.session
    val handler = session.debugProcess.smartStepIntoHandler ?: return
    withContext(Dispatchers.EDT) {
      @Suppress("UNCHECKED_CAST")
      session.smartStepInto(handler as XSmartStepIntoHandler<XSmartStepIntoVariant?>, targetModel.target)
    }
  }

  override suspend fun computeSmartStepTargets(sessionId: XDebugSessionId): List<XSmartStepIntoTargetDto> {
    return computeTargets(sessionId) { handler, position ->
      withContext(Dispatchers.EDT) {
        handler.computeSmartStepVariantsAsync(position).await()
      }
    }
  }

  override suspend fun computeStepTargets(sessionId: XDebugSessionId): List<XSmartStepIntoTargetDto> {
    return computeTargets(sessionId) { handler, position ->
      withContext(Dispatchers.EDT) {
        handler.computeStepIntoVariants(position).await()
      }
    }
  }

  private suspend fun computeTargets(
    sessionId: XDebugSessionId,
    computeVariants: suspend (XSmartStepIntoHandler<*>, XSourcePosition) -> List<XSmartStepIntoVariant>,
  ): List<XSmartStepIntoTargetDto> {
    val session = sessionId.findValue() ?: return emptyList()
    val scope = session.currentSuspendCoroutineScope ?: return emptyList()
    val handler = session.debugProcess.smartStepIntoHandler ?: return emptyList()
    val sourcePosition = session.topFramePosition ?: return emptyList()
    return computeVariants(handler, sourcePosition).map { variant ->
      val id = variant.storeGlobally(scope, session)
      readAction {
        val textRange = variant.highlightRange?.let { it.startOffset to it.endOffset }
        val forced = variant is ForceSmartStepIntoSource && variant.needForceSmartStepInto()
        XSmartStepIntoTargetDto(id, variant.icon?.rpcId(), variant.text, variant.description, textRange, forced)
      }
    }
  }

  override suspend fun forceStepInto(sessionId: XDebugSessionId) {
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.forceStepInto()
    }
  }

  override suspend fun runToPosition(sessionId: XDebugSessionId, sourcePositionDto: XSourcePositionDto, ignoreBreakpoints: Boolean) {
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.runToPosition(sourcePositionDto.sourcePosition(), ignoreBreakpoints)
    }
  }

  override suspend fun triggerUpdate(sessionId: XDebugSessionId) {
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.rebuildViews()
    }
  }

  override suspend fun updateExecutionPosition(sessionId: XDebugSessionId) {
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.updateExecutionPosition()
    }
  }

  override suspend fun onTabInitialized(sessionId: XDebugSessionId, tabInfo: XDebuggerSessionTabInfoCallback) {
    val tab = tabInfo.tab ?: return
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.tabInitialized(tab)
    }
  }

  override suspend fun setCurrentStackFrame(sessionId: XDebugSessionId, executionStackId: XExecutionStackId, frameId: XStackFrameId, isTopFrame: Boolean) {
    val session = sessionId.findValue() ?: return
    val executionStackModel = executionStackId.findValue() ?: return
    val stackFrameModel = frameId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.setCurrentStackFrame(executionStackModel.executionStack, stackFrameModel.stackFrame, isTopFrame)
    }
  }

  override suspend fun computeExecutionStacks(suspendContextId: XSuspendContextId): Flow<XExecutionStacksEvent> {
    val suspendContextModel = suspendContextId.findValue() ?: return emptyFlow()
    return channelFlow {
      suspendContextModel.suspendContext.computeExecutionStacks(object : XSuspendContext.XExecutionStackContainer {
        override fun addExecutionStack(executionStacks: List<XExecutionStack>, last: Boolean) {
          val session = suspendContextModel.session
          val stacks = executionStacks.map { stack ->
            val id = stack.getOrStoreGlobally(suspendContextModel.coroutineScope, session)
            XExecutionStackDto(id, stack.displayName, stack.icon?.rpcId())
          }
          trySend(XExecutionStacksEvent.NewExecutionStacks(stacks, last))
          if (last) {
            this@channelFlow.close()
          }
        }

        override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
          trySend(XExecutionStacksEvent.ErrorOccurred(errorMessage))
        }
      })
      awaitClose()
    }.buffer(Channel.UNLIMITED)
  }

  override suspend fun getFileColorsFlow(sessionId: XDebugSessionId): Flow<XFileColorDto> {
    val session = sessionId.findValue() ?: return emptyFlow()
    return channelFlow {
      session.fileColorsComputer.fileColors.collect { (virtualFile, colorState) ->
        val serializedState = when (colorState) {
          is ColorState.Computed -> SerializedColorState.Computed(colorState.color.rpcId())
          ColorState.Computing -> SerializedColorState.Computing
          ColorState.NoColor -> SerializedColorState.NoColor
        }
        // TODO[IJPL-177087]: send in batches to optimize throughput?
        send(XFileColorDto(virtualFile.rpcId(), serializedState))
      }
    }
  }

  override suspend fun scheduleFileColorComputation(sessionId: XDebugSessionId, virtualFileId: VirtualFileId) {
    val session = sessionId.findValue() ?: return
    val file = virtualFileId.virtualFile() ?: return
    // TODO[IJPL-177087]: collect in batches to optimize throughput?
    session.fileColorsComputer.sendRequest(file)
  }

  override suspend fun switchToTopFrame(sessionId: XDebugSessionId) {
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.showExecutionPoint()
    }
  }

  override suspend fun muteBreakpoints(sessionId: XDebugSessionId, muted: Boolean) {
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.setBreakpointMuted(muted)
    }
  }
}

internal suspend fun XDebugSessionImpl.suspendData(): SuspendData? {
  val suspendContext = suspendContext ?: return null
  val suspendScope = currentSuspendCoroutineScope ?: return null
  val suspendContextId = coroutineScope {
    suspendContext.getOrStoreGlobally(suspendScope, this@suspendData)
  }
  val suspendContextDto = XSuspendContextDto(suspendContextId, suspendContext is XSteppingSuspendContext)
  val executionStackDto = suspendContext.activeExecutionStack?.let {
    val activeExecutionStackId = it.getOrStoreGlobally(suspendScope, this@suspendData)
    XExecutionStackDto(activeExecutionStackId, it.displayName, it.icon?.rpcId())
  }
  val stackTraceDto = currentStackFrame?.let {
    createXStackFrameDto(it, suspendScope, this@suspendData)
  }

  return SuspendData(suspendContextDto,
                     executionStackDto,
                     stackTraceDto)
}

internal fun createXStackFrameDto(frame: XStackFrame, coroutineScope: CoroutineScope, session: XDebugSessionImpl): XStackFrameDto {
  val id = frame.getOrStoreGlobally(coroutineScope, session)
  val equalityObject = frame.equalityObject
  val serializedEqualityObject = when (equalityObject) {
    is String -> XStackFrameStringEqualityObject(equalityObject)
    else -> null // TODO support other types
  }
  val canEvaluateInDocument = frame.isDocumentEvaluator
  val evaluatorDto = XDebuggerEvaluatorDto(canEvaluateInDocument)
  return XStackFrameDto(id, frame.sourcePosition?.toRpc(), serializedEqualityObject, evaluatorDto, frame.initialPresentation(),
                        frame.captionInfo(), frame.customBackgroundInfo(), frame.canDrop(session))
}

private fun XStackFrame.captionInfo(): XStackFrameCaptionInfo {
  return if (this is XDebuggerFramesList.ItemWithSeparatorAbove) {
    XStackFrameCaptionInfo(hasSeparatorAbove(), captionAboveOf)
  }
  else {
    XStackFrameCaptionInfo.noInfo
  }
}

private fun XStackFrame.customBackgroundInfo(): XStackFrameCustomBackgroundInfo? {
  if (this !is XDebuggerFramesList.ItemWithCustomBackgroundColor) {
    return null
  }
  return XStackFrameCustomBackgroundInfo(backgroundColor?.rpcId())
}

private fun XStackFrame.canDrop(session: XDebugSessionImpl): ThreeState {
  val handler = session.debugProcess.dropFrameHandler ?: return ThreeState.NO
  return handler.canDropFrame(this)
}

private fun XStackFrame.initialPresentation(): XStackFramePresentation {
  val parts = mutableListOf<XStackFramePresentationFragment>()
  var iconId: IconId? = null
  var tooltip: String? = null
  customizePresentation(object : ColoredTextContainer {
    override fun append(fragment: @NlsContexts.Label String, attributes: SimpleTextAttributes) {
      parts += XStackFramePresentationFragment(fragment, attributes.toRpc())
    }

    override fun setIcon(icon: Icon?) {
      iconId = icon?.rpcId()
    }

    override fun setToolTipText(text: @NlsContexts.Tooltip String?) {
      tooltip = text
    }
  })
  return XStackFramePresentation(parts, iconId, tooltip)
}
