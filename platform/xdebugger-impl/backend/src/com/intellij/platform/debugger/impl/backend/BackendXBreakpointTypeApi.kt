// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.rpc.DocumentPatchVersion
import com.intellij.ide.rpc.util.toRpc
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.extensions.ExtensionPointAdapter
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.findDocument
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
import com.intellij.xdebugger.impl.breakpoints.*
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.rpc.sourcePosition
import fleet.rpc.core.toRpc
import fleet.util.channels.use
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import org.jetbrains.concurrency.asDeferred
import org.jetbrains.concurrency.await
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
      send(getCurrentBreakpointTypeDtos(project))
      awaitClose()
    }.buffer(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    return XBreakpointTypeList(
      initialTypes,
      typesFlow.toRpc()
    )
  }

  override suspend fun getBreakpointsInfo(projectId: ProjectId, fileId: VirtualFileId, start: Int, endInclusive: Int): List<XBreakpointsLineInfo>? {
    try {
      val project = projectId.findProjectOrNull() ?: return null
      val file = fileId.virtualFile() ?: return null
      val editorBreakpointsRawInfo: List<BreakpointsLineRawInfo?> = readAction {
        val document = file.findDocument()
        blockingContextToIndicator {
          (start..endInclusive).map { line ->
            if (document == null) return@map null
            if (!DocumentUtil.isValidLine(line, document)) return@map null
            ProgressManager.checkCanceled()
            val position = XDebuggerUtil.getInstance().createPosition(file, line) ?: return@map null
            computeBreakpointsLineRawInfo(project, position)
          }
        }
      }

      return editorBreakpointsRawInfo.map {
        it?.toDto() ?: XBreakpointsLineInfo(listOf(), false)
      }
    }
    catch (e: CancellationException) {
      LOG.info("Request getBreakpointsInfo was cancelled: $e")
      return null
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
             "Request details: hasBreakpoints=${request.hasBreakpoints}, isTemporary=${request.isTemporary}, isLogging=${request.isLogging}," +
             "  line breakpoint types: ${lineTypes.map { it.id }}")

    val variants = readAction { XDebuggerUtilImpl.getLineBreakpointVariants(project, lineTypes, position) }.await()

    if (variants.isEmpty()) {
      LOG.info("[$requestId] No variants found, returning XNoBreakpointPossibleResponse")
      return XNoBreakpointPossibleResponse
    }

    val singleVariant = variants.singleOrNull()
    if (singleVariant != null) {
      val variantText = readAction { singleVariant.text }
      LOG.info("[$requestId] Single variant found: $variantText")

      if (request.hasBreakpoints) {
        LOG.info("[$requestId] Breakpoint exists, returning XRemoveBreakpointResponse")
        return XRemoveBreakpointResponse
      }

      val breakpoint = createBreakpointByVariant(project, singleVariant, position, request)
      LOG.info("[$requestId] Created breakpoint: $breakpoint, returning XLineBreakpointInstalledResponse")
      return XLineBreakpointInstalledResponse(breakpoint.breakpointId)
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
        val variantText = readAction { variant.text }
        LOG.info("[$requestId] Received variant selection: $receivedResponse Selected variant: $variantText" +
                 "[$requestId] Created breakpoint from selected variant: $breakpoint")

        it.send(breakpoint.breakpointId)
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
    if (request.isLogging) {
      breakpoint.setSuspendPolicy(SuspendPolicy.NONE)
      if (request.logExpression != null) {
        breakpoint.setLogExpression(request.logExpression)
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

  override suspend fun rememberRemovedBreakpoint(breakpointId: XBreakpointId) {
    val requestId = requestCounter.getAndIncrement()
    LOG.info("[$requestId] Remembering removed breakpoint: $breakpointId")
    val breakpoint = breakpointId.findValue() ?: return
    edtWriteAction {
      (XDebuggerManager.getInstance(breakpoint.project).breakpointManager as XBreakpointManagerImpl).rememberRemovedBreakpoint(breakpoint)
      LOG.info("[$requestId] Remembered removed breakpoint: $breakpointId")
    }
  }

  override suspend fun restoreRemovedBreakpoint(projectId: ProjectId) {
    val requestId = requestCounter.getAndIncrement()
    LOG.info("[$requestId] Restoring removed breakpoint in $projectId")
    val project = projectId.findProjectOrNull() ?: return
    edtWriteAction {
      val restored = (XDebuggerManager.getInstance(project).breakpointManager as XBreakpointManagerImpl).restoreLastRemovedBreakpoint()
      LOG.info("[$requestId] Restored removed breakpoint: ${(restored as? XBreakpointBase<*, *, *>)?.breakpointId}")
    }
  }

  override suspend fun computeInlineBreakpointVariants(projectId: ProjectId, fileId: VirtualFileId, lines: Set<Int>, documentPatchVersion: DocumentPatchVersion?): List<InlineBreakpointVariantsOnLine>? {
    val project = projectId.findProject()
    val file = fileId.virtualFile() ?: return emptyList()
    val document = readAction { file.findDocument() } ?: return emptyList()
    if (!document.awaitIsInSyncAndCommitted(project, documentPatchVersion)) return null
    val lineToVariants = InlineBreakpointsVariantsManager.getInstance(project).calculateBreakpointsVariants(document, lines)
    return lineToVariants.map { (line, variants) ->
      InlineBreakpointVariantsOnLine(line, variants.map { it.toRpc(project, document) })
    }
  }

  override suspend fun createVariantBreakpoint(projectId: ProjectId, fileId: VirtualFileId, line: Int, variantId: XInlineBreakpointVariantId) {
    val project = projectId.findProject()
    val file = fileId.virtualFile() ?: return
    val variant = variantId.findValue() ?: return
    edtWriteAction {
      val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
      XDebuggerUtilImpl.addLineBreakpoint(breakpointManager, variant, file, line)
    }
  }

  override suspend fun copyLineBreakpoint(breakpointId: XBreakpointId, fileId: VirtualFileId, line: Int) {
    val requestId = requestCounter.getAndIncrement()
    val file = fileId.virtualFile() ?: return
    LOG.info("[$requestId] Copying line breakpoint: $breakpointId to file: $file, line: $line")
    val breakpoint = breakpointId.findValue() as? XLineBreakpointImpl<*> ?: return
    val project = breakpoint.project ?: return
    edtWriteAction {
      val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager as XBreakpointManagerImpl
      val breakpointCopy = breakpointManager.copyLineBreakpoint(breakpoint, file.url, line)
      LOG.info("[$requestId] Copied line breakpoint: ${(breakpointCopy as? XBreakpointBase<*, *, *>)?.breakpointId}")
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
  private fun computeBreakpointsLineRawInfo(project: Project, position: XSourcePosition): BreakpointsLineRawInfo {
    val lineBreakpointTypes = XBreakpointUtil.getAvailableLineBreakpointTypes(project, position, selectTypeByPositionColumn = true)
    val variantsPromise = if (lineBreakpointTypes.isNotEmpty()) {
      XDebuggerUtilImpl.getLineBreakpointVariants(project, lineBreakpointTypes, position).asDeferred()
    }
    else {
      CompletableDeferred(listOf())
    }
    return BreakpointsLineRawInfo(lineBreakpointTypes, variantsPromise)
  }

  private class BreakpointsLineRawInfo(
    private val types: List<XBreakpointType<*, *>>,
    private val variantsPromise: Deferred<List<XLineBreakpointType<*>.XLineBreakpointVariant>>,
  ) {
    suspend fun toDto(): XBreakpointsLineInfo {
      return XBreakpointsLineInfo(types.map { XBreakpointTypeId(it.id) }, singleBreakpointVariant = variantsPromise.await().size == 1)
    }
  }
}

@Service(Service.Level.PROJECT)
private class BackendXBreakpointTypeApiProjectCoroutineScope(val cs: CoroutineScope)

private suspend fun InlineVariantWithMatchingBreakpoint.toRpc(project: Project, document: Document): InlineBreakpointVariantWithMatchingBreakpointDto {
  return InlineBreakpointVariantWithMatchingBreakpointDto(
    variant = variant?.toRpc(project, document),
    breakpointId = breakpoint?.breakpointId,
  )
}

private suspend fun XLineBreakpointType<*>.XLineBreakpointVariant.toRpc(project: Project, document: Document): XInlineBreakpointVariantDto {
  return XInlineBreakpointVariantDto(
    InlineBreakpointsIdManager.getInstance(project).createId(this, document),
    highlightRange = readAction { highlightRange?.toRpc() },
    icon = type.enabledIcon.rpcId(),
    tooltipDescription = tooltipDescription,
  )
}
