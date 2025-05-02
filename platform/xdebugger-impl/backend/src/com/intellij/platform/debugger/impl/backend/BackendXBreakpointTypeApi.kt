// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.findEditorOrNull
import com.intellij.openapi.extensions.ExtensionPointAdapter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.project.findProjectOrNull
import com.intellij.util.DocumentUtil
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
import com.intellij.xdebugger.impl.rpc.*
import fleet.rpc.core.toRpc
import fleet.util.channels.use
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import org.jetbrains.concurrency.await

internal class BackendXBreakpointTypeApi : XBreakpointTypeApi {
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

  override suspend fun getAvailableBreakpointTypesForLine(projectId: ProjectId, editorId: EditorId, line: Int): List<XBreakpointTypeId> {
    val project = projectId.findProjectOrNull() ?: return emptyList()
    val editor = editorId.findEditorOrNull() ?: return emptyList()
    val types = readAction {
      blockingContextToIndicator {
        val position = XDebuggerUtil.getInstance().createPosition(FileDocumentManager.getInstance().getFile(editor.document), line)
                       ?: return@blockingContextToIndicator emptyList<XBreakpointType<*, *>>()
        XBreakpointUtil.getAvailableLineBreakpointTypes(project, position, editor)
      }
    }
    return types.map { XBreakpointTypeId(it.id) }
  }

  override suspend fun getAvailableBreakpointTypesForEditor(projectId: ProjectId, editorId: EditorId, start: Int, endInclusive: Int): List<List<XBreakpointTypeId>>? {
    val project = projectId.findProjectOrNull() ?: return null
    val editor = editorId.findEditorOrNull() ?: return null
    return readAction {
      blockingContextToIndicator {
        val editorBreakpointTypes = mutableListOf<List<XBreakpointTypeId>>()
        for (line in start..endInclusive) {
          if (!DocumentUtil.isValidLine(line, editor.document)) {
            continue
          }
          ProgressManager.checkCanceled()
          val position = XDebuggerUtil.getInstance().createPosition(FileDocumentManager.getInstance().getFile(editor.document), line)
          if (position == null) {
            editorBreakpointTypes.add(emptyList())
            continue
          }
          val lineBreakpointTypes = XBreakpointUtil.getAvailableLineBreakpointTypes(project, position, editor)
          editorBreakpointTypes.add(lineBreakpointTypes.map { XBreakpointTypeId(it.id) })
        }
        editorBreakpointTypes
      }
    }
  }

  override suspend fun addBreakpointThroughLux(projectId: ProjectId, typeId: XBreakpointTypeId): Deferred<XBreakpointDto?> {
    val project = projectId.findProjectOrNull() ?: return CompletableDeferred<XBreakpointDto?>(value = null)
    val type = XBreakpointUtil.findType(typeId.id) ?: return CompletableDeferred<XBreakpointDto?>(value = null)
    return project.service<BackendXBreakpointTypeApiProjectCoroutineScope>().cs.async(Dispatchers.EDT) {
      val rawBreakpoint = type.addBreakpoint(project, null)
      (rawBreakpoint as? XBreakpointBase<*, *, *>)?.toRpc()
    }
  }

  override suspend fun toggleLineBreakpoint(projectId: ProjectId, request: XLineBreakpointInstallationRequest): XToggleLineBreakpointResponse? {
    val project = projectId.findProjectOrNull() ?: return null
    val position = request.position.sourcePosition()
    val lineTypes = request.types.mapNotNull { XBreakpointUtil.findType(it.id) as? XLineBreakpointType<*> }
    val variants = readAction { XDebuggerUtilImpl.getLineBreakpointVariants(project, lineTypes, position) }.await()
    if (variants.isEmpty()) return XLineBreakpointInstalledResponse(null)
    val singleVariant = variants.singleOrNull()
    if (singleVariant != null) {
      if (request.willRemoveBreakpointIfSingleVariant) {
        return XRemoveBreakpointResponse
      }
      val breakpoint = createBreakpointByVariant(project, singleVariant, position, request)
      return XLineBreakpointInstalledResponse(breakpoint?.toRpc())
    }
    val variantDtos = readAction {
      variants.map {
        XLineBreakpointVariantDto(it.text, it.icon?.rpcId(), it.highlightRange?.toRpc(),
                                  it.getPriority(project), it.shouldUseAsInlineVariant())
      }
    }
    val selectionCallback = Channel<VariantSelectedResponse>()
    project.service<BackendXBreakpointTypeApiProjectCoroutineScope>().cs.launch {
      val (selectedVariantIndex, breakpointCallback) = selectionCallback.receiveCatching().getOrNull() ?: return@launch
      breakpointCallback.use {
        val variant = variants[selectedVariantIndex]
        val breakpoint = createBreakpointByVariant(project, variant, position, request)
        it.send(breakpoint?.toRpc())
      }
    }
    return XLineBreakpointMultipleVariantResponse(variantDtos, selectionCallback)
  }

  private suspend fun createBreakpointByVariant(
    project: Project,
    variant: XLineBreakpointType<XBreakpointProperties<*>>.XLineBreakpointVariant,
    position: XSourcePosition,
    request: XLineBreakpointInstallationRequest,
  ): XBreakpointBase<*, *, *>? {
    val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
    val breakpoint = readAction {
      XDebuggerUtilImpl.addLineBreakpoint(breakpointManager, variant, position.file, position.line, request.isTemporary)
    }
    if (breakpoint != null && request.isConditional) {
      breakpoint.setSuspendPolicy(SuspendPolicy.NONE)
      if (request.condition != null) {
        breakpoint.setLogExpression(request.condition)
      }
      else {
        breakpoint.setLogMessage(true)
      }
    }
    return breakpoint as? XBreakpointBase<*, *, *>
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
}

@Service(Service.Level.PROJECT)
private class BackendXBreakpointTypeApiProjectCoroutineScope(val cs: CoroutineScope)