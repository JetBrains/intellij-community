// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.findEditorOrNull
import com.intellij.openapi.extensions.ExtensionPointAdapter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.project.findProjectOrNull
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.intellij.xdebugger.impl.rpc.XBreakpointId
import com.intellij.xdebugger.impl.rpc.XBreakpointTypeId
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.rpc.toRpc
import fleet.rpc.core.toRpc
import fleet.util.channels.use
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.await
import org.jetbrains.concurrency.resolvedPromise
import java.util.concurrent.atomic.AtomicInteger

private val LOG = logger<BackendXBreakpointTypeApi>()

internal class BackendXBreakpointTypeApi : XBreakpointTypeApi {
  private val requestCounter = AtomicInteger()

  override suspend fun getBreakpointTypeList(project: ProjectId): XBreakpointTypeList {
    val project = project.findProject()
    val initialTypes = getCurrentBreakpointTypeDtos(project)
    val typesFlow = channelFlow {
      val channelCs = this@channelFlow
      XBreakpointType.EXTENSION_POINT_NAME.addExtensionPointListener(channelCs, object : ExtensionPointAdapter<XBreakpointType<*, *>>() {
        override fun extensionListChanged() {
          trySend(getCurrentBreakpointTypeDtos(project))
        }
      })
      trySend(getCurrentBreakpointTypeDtos(project))
    }.buffer(1)

    return XBreakpointTypeList(
      initialTypes,
      typesFlow.toRpc()
    )
  }

  override suspend fun getBreakpointsInfoForLine(projectId: ProjectId, editorId: EditorId, line: Int): XBreakpointsLineInfo {
    val project = projectId.findProjectOrNull() ?: return XBreakpointsLineInfo(listOf(), false)
    val editor = editorId.findEditorOrNull() ?: return XBreakpointsLineInfo(listOf(), false)
    val rawInfo: BreakpointsLineRawInfo? = readAction {
      if (!DocumentUtil.isValidLine(line, editor.document)) return@readAction null
      blockingContextToIndicator {
        val position = XDebuggerUtil.getInstance().createPosition(FileDocumentManager.getInstance().getFile(editor.document), line)
                       ?: return@blockingContextToIndicator null
        computeBreakpointsLineRawInfo(project, position, editor)
      }
    }
    return rawInfo?.toDto() ?: XBreakpointsLineInfo(listOf(), false)
  }

  override suspend fun getBreakpointsInfoForEditor(projectId: ProjectId, editorId: EditorId, start: Int, endInclusive: Int): List<XBreakpointsLineInfo>? {
    val project = projectId.findProjectOrNull() ?: return null
    val editor = editorId.findEditorOrNull() ?: return null
    val editorBreakpointsRawInfo: List<BreakpointsLineRawInfo?> = readAction {
      blockingContextToIndicator {
        val editorBreakpointLinesRawInfo = mutableListOf<BreakpointsLineRawInfo?>()
        for (line in start..endInclusive) {
          if (!DocumentUtil.isValidLine(line, editor.document)) {
            continue
          }
          ProgressManager.checkCanceled()
          val position = XDebuggerUtil.getInstance().createPosition(FileDocumentManager.getInstance().getFile(editor.document), line)
          if (position == null) {
            editorBreakpointLinesRawInfo.add(null)
            continue
          }
          editorBreakpointLinesRawInfo.add(computeBreakpointsLineRawInfo(project, position, editor))
        }
        editorBreakpointLinesRawInfo
      }
    }

    return editorBreakpointsRawInfo.map {
      it?.toDto() ?: XBreakpointsLineInfo(listOf(), false)
    }
  }

  override suspend fun addBreakpointThroughLux(projectId: ProjectId, typeId: XBreakpointTypeId): TimeoutSafeResult<XBreakpointDto?> {
    val project = projectId.findProjectOrNull() ?: return CompletableDeferred<XBreakpointDto?>(value = null)
    val type = XBreakpointUtil.findType(typeId.id) ?: return CompletableDeferred<XBreakpointDto?>(value = null)
    return project.service<BackendXBreakpointTypeApiProjectCoroutineScope>().cs.async(Dispatchers.EDT) {
      val requestId = requestCounter.getAndIncrement()
      val rawBreakpoint = type.addBreakpoint(project, null)
      val xBreakpointBase = rawBreakpoint as? XBreakpointBase<*, *, *>
      LOG.info("[$requestId] Adding breakpoint through lux: ${xBreakpointBase?.breakpointId}")
      xBreakpointBase?.toRpc()
    }
  }

  override suspend fun toggleLineBreakpoint(projectId: ProjectId, request: XLineBreakpointInstallationRequest): XToggleLineBreakpointResponse? {
    val requestId = requestCounter.getAndIncrement()
    val project = projectId.findProjectOrNull() ?: return null
    val position = request.position.sourcePosition()
    val lineTypes = request.types.mapNotNull { XBreakpointUtil.findType(it.id) as? XLineBreakpointType<*> }
    LOG.info("[$requestId] Toggle line breakpoint request received file: ${request.position}, line: ${request.position.line}" +
             "Request details: hasBreakpoints=${request.hasBreakpoints}, isTemporary=${request.isTemporary}, isConditional=${request.isConditional}," +
             "  line breakpoint types: ${lineTypes.map { it.id }}")

    val variants = readAction { XDebuggerUtilImpl.getLineBreakpointVariants(project, lineTypes, position) }.await()

    if (variants.isEmpty()) {
      LOG.info("[$requestId] No variants found, returning XNoBreakpointPossibleResponse")
      return XNoBreakpointPossibleResponse
    }

    val singleVariant = variants.singleOrNull()
    if (singleVariant != null) {
      LOG.info("[$requestId] Single variant found: ${singleVariant.text}")

      if (request.hasBreakpoints) {
        LOG.info("[$requestId] Breakpoint exists, returning XRemoveBreakpointResponse")
        return XRemoveBreakpointResponse
      }

      val breakpoint = createBreakpointByVariant(project, singleVariant, position, request)
      LOG.info("[$requestId] Created breakpoint: $breakpoint, returning XLineBreakpointInstalledResponse")
      return XLineBreakpointInstalledResponse(breakpoint.toRpc())
    }

    LOG.info("[$requestId] Multiple variants found (${variants.size}), creating selection dialog")

    val variantDtos = readAction {
      variants.map {
        XLineBreakpointVariantDto(it.text, it.icon?.rpcId(), it.highlightRange?.toRpc(),
                                  it.getPriority(project), it.shouldUseAsInlineVariant())
      }
    }

    val selectionCallback = Channel<VariantSelectedResponse>()
    project.service<BackendXBreakpointTypeApiProjectCoroutineScope>().cs.launch {
      val receivedResponse = try {
        selectionCallback.receive()
      }
      catch (_: ClosedReceiveChannelException) {
        return@launch
      }

      val (selectedVariantIndex, breakpointCallback) = receivedResponse
      breakpointCallback.use {
        val variant = variants[selectedVariantIndex]
        val breakpoint = createBreakpointByVariant(project, variant, position, request)
        LOG.info("[$requestId] Received variant selection: $receivedResponse Selected variant: ${variant.text}" +
                 "[$requestId] Created breakpoint from selected variant: $breakpoint")

        it.send(breakpoint.toRpc())
      }
    }

    return XLineBreakpointMultipleVariantResponse(variantDtos, selectionCallback)
  }

  private suspend fun createBreakpointByVariant(
    project: Project,
    variant: XLineBreakpointType<XBreakpointProperties<*>>.XLineBreakpointVariant,
    position: XSourcePosition,
    request: XLineBreakpointInstallationRequest,
  ): XBreakpointBase<*, *, *> {
    val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
    val breakpoint = readAction {
      XDebuggerUtilImpl.addLineBreakpoint(breakpointManager, variant, position.file, position.line, request.isTemporary)
    }
    if (request.isConditional) {
      breakpoint.setSuspendPolicy(SuspendPolicy.NONE)
      if (request.condition != null) {
        breakpoint.setLogExpression(request.condition)
      }
      else {
        breakpoint.setLogMessage(true)
      }
    }
    return breakpoint as XBreakpointBase<*, *, *>
  }

  override suspend fun removeBreakpoint(breakpointId: XBreakpointId) {
    val requestId = requestCounter.getAndIncrement()
    LOG.info("[$requestId] Removing breakpoint: $breakpointId")
    val breakpoint = breakpointId.findValue() ?: return
    edtWriteAction {
      XDebuggerManager.getInstance(breakpoint.project).breakpointManager.removeBreakpoint(breakpoint)
      LOG.info("[$requestId] Breakpoint removed: $breakpointId")
    }
  }

  private fun getCurrentBreakpointTypeDtos(project: Project): List<XBreakpointTypeDto> {
    return XBreakpointType.EXTENSION_POINT_NAME.extensionList.map { it.toRpc(project) }
  }

  private fun XBreakpointType<*, *>.toRpc(project: Project): XBreakpointTypeDto {
    val lineTypeInfo = if (this is XLineBreakpointType<*>) {
      XLineBreakpointTypeInfo(priority)
    }
    else {
      null
    }
    val index = XBreakpointType.EXTENSION_POINT_NAME.extensionList.indexOf(this)
    val defaultState = (XDebuggerManager.getInstance(project).breakpointManager as XBreakpointManagerImpl).getBreakpointDefaults(this)
    val icons = XBreakpointTypeIcons(
      enabledIcon = enabledIcon.rpcId(),
      disabledIcon = disabledIcon.rpcId(),
      suspendNoneIcon = suspendNoneIcon.rpcId(),
      mutedEnabledIcon = mutedEnabledIcon.rpcId(),
      mutedDisabledIcon = mutedDisabledIcon.rpcId(),
      pendingIcon = pendingIcon?.rpcId(),
      inactiveDependentIcon = inactiveDependentIcon.rpcId(),
      temporaryIcon = (this as? XLineBreakpointType<*>)?.temporaryIcon?.rpcId(),
    )
    // TODO: do we need to subscribe on [defaultState] changes?
    return XBreakpointTypeDto(
      XBreakpointTypeId(id), index, title, isSuspendThreadSupported, lineTypeInfo, defaultState.suspendPolicy,
      standardPanels = visibleStandardPanels.mapTo(mutableSetOf()) { it.toDto() },
      isAddBreakpointButtonVisible,
      icons
    )
  }

  private fun XBreakpointType.StandardPanels.toDto(): XBreakpointTypeSerializableStandardPanels {
    return when (this) {
      XBreakpointType.StandardPanels.SUSPEND_POLICY -> XBreakpointTypeSerializableStandardPanels.SUSPEND_POLICY
      XBreakpointType.StandardPanels.ACTIONS -> XBreakpointTypeSerializableStandardPanels.ACTIONS
      XBreakpointType.StandardPanels.DEPENDENCY -> XBreakpointTypeSerializableStandardPanels.DEPENDENCY
    }
  }

  @RequiresReadLock
  private fun computeBreakpointsLineRawInfo(project: Project, position: XSourcePosition, editor: Editor): BreakpointsLineRawInfo {
    val lineBreakpointTypes = XBreakpointUtil.getAvailableLineBreakpointTypes(project, position, editor)
    val variantsPromise: Promise<List<XLineBreakpointType<*>.XLineBreakpointVariant>> = if (lineBreakpointTypes.isNotEmpty()) {
      XDebuggerUtilImpl.getLineBreakpointVariants(project, lineBreakpointTypes, position)
    }
    else {
      resolvedPromise(listOf())
    }
    return BreakpointsLineRawInfo(lineBreakpointTypes, variantsPromise)
  }

  private class BreakpointsLineRawInfo(
    private val types: List<XBreakpointType<*, *>>,
    private val variantsPromise: Promise<List<XLineBreakpointType<*>.XLineBreakpointVariant>>,
  ) {
    suspend fun toDto(): XBreakpointsLineInfo {
      return XBreakpointsLineInfo(types.map { XBreakpointTypeId(it.id) }, singleBreakpointVariant = variantsPromise.await().size == 1)
    }
  }
}

@Service(Service.Level.PROJECT)
private class BackendXBreakpointTypeApiProjectCoroutineScope(val cs: CoroutineScope)
