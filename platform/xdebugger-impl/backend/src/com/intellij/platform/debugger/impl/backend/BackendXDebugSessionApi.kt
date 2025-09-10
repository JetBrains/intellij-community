// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.rpc.FrontendDocumentId
import com.intellij.ide.rpc.bindToFrontend
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.util.ThreeState
import com.intellij.util.asDisposable
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XSteppingSuspendContext
import com.intellij.xdebugger.impl.frame.ColorState
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XExecutionStackId
import com.intellij.xdebugger.impl.rpc.XStackFrameId
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.rpc.models.getOrStoreGlobally
import com.intellij.xdebugger.impl.rpc.models.storeGlobally
import com.intellij.xdebugger.stepping.ForceSmartStepIntoSource
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant
import fleet.rpc.core.toRpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.await

internal class BackendXDebugSessionApi : XDebugSessionApi {
  override suspend fun createDocument(frontendDocumentId: FrontendDocumentId, sessionId: XDebugSessionId, expression: XExpressionDto, sourcePosition: XSourcePositionDto?, evaluationMode: EvaluationMode): XExpressionDocumentDto? {
    val session = sessionId.findValue() ?: return null
    val project = session.project
    val editorsProvider = session.debugProcess.editorsProvider
    return createBackendDocument(project, frontendDocumentId, editorsProvider, expression, sourcePosition, evaluationMode)
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

  override suspend fun setCurrentStackFrame(sessionId: XDebugSessionId, executionStackId: XExecutionStackId, frameId: XStackFrameId, isTopFrame: Boolean, changedByUser: Boolean) {
    val session = sessionId.findValue() ?: return
    val executionStackModel = executionStackId.findValue() ?: return
    val stackFrameModel = frameId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.setCurrentStackFrame(executionStackModel.executionStack, stackFrameModel.stackFrame, isTopFrame, changedByUser = changedByUser)
    }
  }

  override suspend fun computeExecutionStacks(suspendContextId: XSuspendContextId): Flow<XExecutionStacksEvent> {
    val suspendContextModel = suspendContextId.findValue() ?: return emptyFlow()
    return channelFlow {
      suspendContextModel.suspendContext.computeExecutionStacks(object : XSuspendContext.XExecutionStackContainer {
        override fun addExecutionStack(executionStacks: List<XExecutionStack>, last: Boolean) {
          val session = suspendContextModel.session
          val stacks = executionStacks.map { stack ->
            stack.toRpc(suspendContextModel.coroutineScope, session)
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

internal suspend fun createBackendDocument(
  project: Project,
  frontendDocumentId: FrontendDocumentId,
  editorsProvider: XDebuggerEditorsProvider,
  expression: XExpressionDto,
  sourcePosition: XSourcePositionDto?,
  evaluationMode: EvaluationMode,
): XExpressionDocumentDto {
  return withContext(Dispatchers.EDT) {
    val originalExpression = expression.xExpression()
    val backendDocument = editorsProvider.createDocument(project, originalExpression, sourcePosition?.sourcePosition(), evaluationMode)
    val backendDocumentId = backendDocument.bindToFrontend(frontendDocumentId, project)
    val expressionFlow = channelFlow {
      backendDocument.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          val updatedExpression = editorsProvider.createExpression(project, backendDocument, originalExpression.language, evaluationMode)
          trySend(updatedExpression.toRpc())
        }
      }, this.asDisposable())
      awaitClose()
    }.buffer(1, BufferOverflow.DROP_OLDEST)
    XExpressionDocumentDto(backendDocumentId, expressionFlow.toRpc())
  }
}

internal fun XDebugSessionImpl.suspendData(): SuspendData? {
  val suspendContext = suspendContext ?: return null
  val suspendScope = currentSuspendCoroutineScope ?: return null
  val suspendContextId = suspendContext.getOrStoreGlobally(suspendScope, this)
  val suspendContextDto = XSuspendContextDto(suspendContextId, suspendContext is XSteppingSuspendContext)
  val executionStackDto = suspendContext.activeExecutionStack?.toRpc(suspendScope, this)
  val stackFrameDto = currentStackFrame?.toRpc(suspendScope, this)
  val sourcePositionDto = currentPosition?.toRpc()
  val topSourcePositionDto = topFramePosition?.toRpc()

  return SuspendData(
    suspendContextDto,
    executionStackDto,
    stackFrameDto,
    sourcePositionDto,
    topSourcePositionDto,
  )
}

internal fun XStackFrame.toRpc(coroutineScope: CoroutineScope, session: XDebugSessionImpl): XStackFrameDto {
  val id = getOrStoreGlobally(coroutineScope, session)
  val serializedEqualityObject = when (val equalityObject = equalityObject) {
    is String -> XStackFrameStringEqualityObject(equalityObject)
    else -> null // TODO support other types
  }
  val evaluatorDto = XDebuggerEvaluatorDto(isDocumentEvaluator)
  return XStackFrameDto(id, sourcePosition?.toRpc(), serializedEqualityObject, evaluatorDto, computeTextPresentation(),
                        captionInfo(), customBackgroundInfo(), canDrop(session))
}

internal fun XExecutionStack.toRpc(coroutineScope: CoroutineScope, session: XDebugSessionImpl): XExecutionStackDto {
  val id = getOrStoreGlobally(coroutineScope, session)
  return XExecutionStackDto(
    id,
    displayName,
    icon?.rpcId(),
    xExecutionStackDescriptorAsync?.asDeferred()
  )
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

internal fun XStackFrame.computeTextPresentation(): XStackFramePresentation {
  val parts = mutableListOf<XStackFramePresentationFragment>()
  customizeTextPresentation { fragment, attributes -> parts += XStackFramePresentationFragment(fragment, attributes.toRpc()) }
  return XStackFramePresentation(parts, null, null)
}
