// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.readAction
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
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.intellij.xdebugger.impl.rpc.*
import fleet.rpc.core.toRpc
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow

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
    val customPanels = XBreakpointTypeCustomPanels(
      customPropertiesPanelProvider = {
        this.createCustomPropertiesPanel(project) as? XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?
      },
      customConditionsPanelProvider = {
        this.createCustomConditionsPanel() as? XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?
      },
      customRightPropertiesPanelProvider = {
        this.createCustomRightPropertiesPanel(project) as? XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?
      },
      customTopPropertiesPanelProvider = {
        this.createCustomTopPropertiesPanel(project) as? XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?
      }
    )
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
      customPanels, icons
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