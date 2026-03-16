// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.rpc.FrontendDocumentId
import com.intellij.ide.rpc.bindToFrontend
import com.intellij.ide.rpc.util.toRpc
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.util.coroutines.attachAsChildTo
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.FileColorManager
import com.intellij.util.ThreeState
import com.intellij.util.asDisposable
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XExecutionStackGroup
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XSourceKind
import com.intellij.xdebugger.impl.XSteppingSuspendContext
import com.intellij.xdebugger.impl.frame.XStackFrameWithCustomBackgroundColor
import com.intellij.xdebugger.impl.frame.XStackFrameWithSeparatorAbove
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.rpc.models.getOrStoreGlobally
import com.intellij.xdebugger.impl.rpc.models.storeGlobally
import com.intellij.xdebugger.impl.rpc.sourcePosition
import com.intellij.xdebugger.impl.rpc.toRpc
import com.intellij.xdebugger.stepping.ForceSmartStepIntoSource
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant
import fleet.rpc.core.toRpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.await
import org.jetbrains.concurrency.rejectedPromise
import kotlin.collections.map

internal class BackendXDebugSessionApi : XDebugSessionApi {
  override suspend fun createDocument(frontendDocumentId: FrontendDocumentId, sessionId: XDebugSessionId, expression: XExpressionDto, sourcePosition: XSourcePositionDto?, evaluationMode: EvaluationMode): XExpressionDocumentDto? {
    val session = sessionId.findValue() ?: return null
    val project = session.project
    val editorsProvider = session.debugProcess.editorsProvider
    return createBackendDocument(project, frontendDocumentId, editorsProvider, expression, sourcePosition, evaluationMode)
  }

  override suspend fun supportedLanguages(projectId: ProjectId, editorsProviderId: XDebuggerEditorsProviderId, sourcePositionDto: XSourcePositionDto?): List<LanguageDto> {
    val project = projectId.findProject()
    val editorsProvider = editorsProviderId.findValue() ?: return emptyList()
    val position = sourcePositionDto?.sourcePosition()
    return smartReadAction(project) {
      editorsProvider.getSupportedLanguages(project, position)
    }.map { it.toRpc() }
  }

  override suspend fun getAlternativeSourceKindFlow(sessionId: XDebugSessionId): Flow<Boolean> {
    val session = sessionId.findValue() ?: return emptyFlow()
    return session.alternativeSourceKindState
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

  override suspend fun computeSmartStepTargets(sessionId: XDebugSessionId): List<XSmartStepIntoTargetDto>? {
    return computeTargets(sessionId) { handler, position ->
      withContext(Dispatchers.EDT) {
        handler.computeSmartStepVariantsAsync(position).awaitOrNullIfRejected()
      }
    }
  }

  override suspend fun computeStepTargets(sessionId: XDebugSessionId): List<XSmartStepIntoTargetDto>? {
    return computeTargets(sessionId) { handler, position ->
      withContext(Dispatchers.EDT) {
        handler.computeStepIntoVariants(position).awaitOrNullIfRejected()
      }
    }
  }

  private suspend fun computeTargets(
    sessionId: XDebugSessionId,
    computeVariants: suspend (XSmartStepIntoHandler<*>, XSourcePosition) -> List<XSmartStepIntoVariant>?,
  ): List<XSmartStepIntoTargetDto>? {
    val session = sessionId.findValue() ?: return null
    val scope = session.getSuspendContextModel()?.coroutineScope ?: return null
    val handler = session.debugProcess.smartStepIntoHandler ?: return null
    val sourcePosition = session.topFramePosition ?: return null
    try {
      return computeVariants(handler, sourcePosition)?.map { variant ->
        val id = variant.storeGlobally(scope, session)
        readAction {
          val textRange = variant.highlightRange?.toRpc()
          val forced = variant is ForceSmartStepIntoSource && variant.needForceSmartStepInto()
          XSmartStepIntoTargetDto(id, variant.icon?.rpcId(), variant.text, variant.description, textRange, forced)
        }
      }
    }
    catch (_: IndexNotReadyException) {
      return null
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
      session.frontendUpdate()
    }
  }

  override suspend fun setCurrentStackFrame(
    sessionId: XDebugSessionId,
    suspendContextId: XSuspendContextId,
    executionStackId: XExecutionStackId,
    frameId: XStackFrameId,
    isTopFrame: Boolean,
  ) {
    withContext(Dispatchers.EDT) {
      val session = sessionId.findValue() ?: return@withContext
      val suspendContextModel = suspendContextId.findValue() ?: return@withContext
      val executionStackModel = executionStackId.findValue() ?: return@withContext
      val stackFrameModel = frameId.findValue() ?: return@withContext

      session.setCurrentStackFrame(suspendContextModel.suspendContext,
                                   executionStackModel.executionStack,
                                   stackFrameModel.stackFrame,
                                   isTopFrame,
                                   changedByUser = true)
    }
  }

  override suspend fun computeRunningExecutionStacks(sessionId: XDebugSessionId, suspendContextId: XSuspendContextId?): Flow<XExecutionStackGroupsEvent> {
    val session = sessionId.findValue() ?: return emptyFlow()
    val suspendContextModel = suspendContextId?.findValue()
    val scope = session.coroutineScope.childScopeCancelledOnSessionEvents("RunningExecutionStacksScope", session)
    return createExecutionStackGroupEventFlow(session, scope) { container ->
      session.debugProcess.computeRunningExecutionStacks(container, suspendContextModel?.suspendContext)
    }
  }

  override suspend fun computeExecutionStacks(suspendContextId: XSuspendContextId): Flow<XExecutionStacksEvent> {
    val suspendContextModel = suspendContextId.findValue() ?: return emptyFlow()
    val session = suspendContextModel.session
    return createExecutionStacksEventFlow(session, suspendContextModel.coroutineScope) { container ->
      suspendContextModel.suspendContext.computeExecutionStacks(container)
    }
  }

  private fun createExecutionStacksEventFlow(
    session: XDebugSessionImpl,
    scope: CoroutineScope,
    computeExecutionStacks: (XSuspendContext.XExecutionStackContainer) -> Unit
  ) : Flow<XExecutionStacksEvent> {
    return channelFlow {
      attachAsChildTo(scope)
      val events = Channel<suspend () -> XExecutionStacksEvent>(Channel.UNLIMITED)
      val container = object : XSuspendContext.XExecutionStackContainer {
        @Volatile
        var obsolete = false

        override fun isObsolete(): Boolean {
          return obsolete
        }

        override fun addExecutionStack(executionStacks: List<XExecutionStack>, last: Boolean) {
          events.trySend {
            val stacks = executionStacks.map { stack ->
              stack.toRpc(scope, session)
            }
            NewExecutionStacksEvent(stacks, last)
          }
        }

        override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
          events.trySend {
            ErrorOccurredEvent(errorMessage)
          }
        }
      }

      computeExecutionStacks(container)

      for (event in events) {
        val eventResult = event()
        trySend(eventResult)
        if (eventResult is NewExecutionStacksEvent && eventResult.last) {
          this@channelFlow.close()
        }
      }

      awaitClose {
        container.obsolete = true
      }
    }.buffer(Channel.UNLIMITED)
  }

  private fun createExecutionStackGroupEventFlow(
    session: XDebugSessionImpl,
    scope: CoroutineScope,
    computeExecutionStacks: (XSuspendContext.XExecutionStackGroupContainer) -> Unit
  ) : Flow<XExecutionStackGroupsEvent> {
    return channelFlow {
      attachAsChildTo(scope)
      val events = Channel<suspend () -> XExecutionStackGroupsEvent>(Channel.UNLIMITED)
      val container = object : XSuspendContext.XExecutionStackGroupContainer {
        @Volatile
        var obsolete = false

        override fun isObsolete(): Boolean {
          return obsolete
        }

        override fun addExecutionStack(executionStacks: List<XExecutionStack>, last: Boolean) {
          events.trySend {
            val stacks = executionStacks.map { stack ->
              stack.toRpc(scope, session)
            }
            NewExecutionStacksEvent(stacks, last)
          }
        }

        override fun addExecutionStackGroups(executionStackGroups: List<XExecutionStackGroup>, last: Boolean) {
          events.trySend {
            val groups = executionStackGroups.map { group ->
              group.toRpc(scope, session)
            }
            NewExecutionStackGroupsEvent(groups, last)
          }
        }

        override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
          events.trySend {
            ErrorOccurredEvent(errorMessage)
          }
        }
      }
      computeExecutionStacks(container)

      for (event in events) {
        val eventResult = event()
        trySend(eventResult)
        if (eventResult is NewExecutionStacksEvent && eventResult.last) {
          this@channelFlow.close()
        }
        if (eventResult is NewExecutionStackGroupsEvent && eventResult.last) {
          this@channelFlow.close()
        }
      }

      awaitClose {
        container.obsolete = true
      }
    }.buffer(Channel.UNLIMITED)
  }

  // TODO use a util function from the shared module
  private fun CoroutineScope.childScopeCancelledOnSessionEvents(name: String, session: XDebugSessionImpl): CoroutineScope =
    childScope(name).also { childScope ->
      val listener = object : XDebugSessionListener {
        override fun sessionPaused() { childScope.cancel() }

        override fun sessionResumed() { childScope.cancel() }

        override fun sessionStopped() { childScope.cancel() }
      }
      session.addSessionListener(listener, childScope.asDisposable())
    }

  override suspend fun muteBreakpoints(sessionDataId: XDebugSessionDataId, muted: Boolean) {
    val session = sessionDataId.findValue()?.session ?: return
    withContext(Dispatchers.EDT) {
      session.setBreakpointMuted(muted)
    }
  }

  override suspend fun getUiUpdateEventsFlow(sessionId: XDebugSessionId): Flow<Unit> {
    val session = sessionId.findValue() ?: return emptyFlow()
    val eventsProvider = session.debugProcess.sessionEventsProvider ?: return emptyFlow()
    return eventsProvider.getUiUpdateEventsFlow()
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
      val changedFlow = MutableSharedFlow<Unit>(1, 1, BufferOverflow.DROP_OLDEST)
      backendDocument.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          changedFlow.tryEmit(Unit)
        }
      }, this.asDisposable())
      changedFlow.collectLatest {
        // Some implementations might rely on PSI
        backendDocument.awaitCommited(project)
        val updatedExpression = editorsProvider.createExpression(project, backendDocument, originalExpression.language, evaluationMode)
        send(updatedExpression.toRpc())
      }
    }
    XExpressionDocumentDto(backendDocumentId, expressionFlow.toRpc())
  }
}

internal suspend fun XDebugSessionImpl.suspendData(): SuspendData? {
  val currentSuspendContextModel = getSuspendContextModel() ?: return null
  val suspendContext = currentSuspendContextModel.suspendContext
  val suspendScope = currentSuspendContextModel.coroutineScope
  val suspendContextDto = XSuspendContextDto(currentSuspendContextModel.id, suspendContext is XSteppingSuspendContext)
  val executionStackDto = suspendContext.activeExecutionStack?.toRpc(suspendScope, this)
  val stackFrameDto = currentStackFrame?.toRpc(suspendScope, this)
  val topSourcePositionDto = topFramePosition?.toRpc()

  return SuspendData(
    suspendContextDto,
    executionStackDto,
    stackFrameDto,
    topSourcePositionDto,
  )
}

internal fun XStackFrame.toRpc(coroutineScope: CoroutineScope, session: XDebugSessionImpl): XStackFrameDto {
  val id = getOrStoreGlobally(coroutineScope, session)
  val serializedEqualityObject = when (val equalityObject = equalityObject) {
    is String -> XStackFrameStringEqualityObject(equalityObject)
    null -> null
    // TODO support other types better?
    else -> XStackFrameStringEqualityObject(equalityObject.toString())
  }
  val evaluatorDto = XDebuggerEvaluatorDto(isDocumentEvaluator)
  val alternativeSourcePosition = session.getFrameSourcePosition(this, XSourceKind.ALTERNATIVE)
  return XStackFrameDto(
    id,
    sourcePosition?.toRpc(),
    alternativeSourcePosition?.toRpc(),
    serializedEqualityObject,
    evaluatorDto,
    computeTextPresentation(),
    captionInfo(),
    backgroundInfo(session.project),
    canDrop(session)
  )
}

internal suspend fun XExecutionStack.toRpc(coroutineScope: CoroutineScope, session: XDebugSessionImpl): XExecutionStackDto {
  val (stack, id) = getOrStoreGlobally(coroutineScope, session)
  return XExecutionStackDto(
    id,
    stack.displayName,
    stack.icon?.rpcId(),
    stack.iconFlow.map { it?.rpcId() }.toRpc(),
    stack.xExecutionStackDescriptorAsync?.asDeferred(),
    stack.topFrameAsync.thenApply { frame ->
      frame?.toRpc(coroutineScope, session)
    }.asDeferred()
  )
}

internal suspend fun XExecutionStackGroup.toRpc(coroutineScope: CoroutineScope, session: XDebugSessionImpl): XExecutionStackGroupDto {
  return XExecutionStackGroupDto(
    groups.map { it.toRpc(coroutineScope, session) },
    stacks.map { it.toRpc(coroutineScope, session) },
    this.name,
    this.icon?.rpcId(),
  )
}

private fun XStackFrame.captionInfo(): XStackFrameCaptionInfo {
  return if (this is XStackFrameWithSeparatorAbove) {
    XStackFrameCaptionInfo(hasSeparatorAbove(), captionAboveOf)
  }
  else {
    XStackFrameCaptionInfo.noInfo
  }
}

private fun XStackFrame.backgroundInfo(project: Project): XStackFrameBackgroundColor? {
  if (this is XStackFrameWithCustomBackgroundColor) {
    return XStackFrameBackgroundColor(backgroundColor?.rpcId())
  }
  val file = sourcePosition?.file ?: return null
  val fileColor = runReadAction {
    FileColorManager.getInstance(project).getFileColor(file)
  } ?: return null
  return XStackFrameBackgroundColor(fileColor.rpcId())
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

private suspend fun <T> Promise<T>.awaitOrNullIfRejected(): T? {
  if (this === rejectedPromise<T>()) return null
  return await()
}
