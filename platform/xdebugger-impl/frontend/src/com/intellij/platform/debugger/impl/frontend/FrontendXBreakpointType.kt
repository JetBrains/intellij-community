// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.platform.project.projectId
import com.intellij.util.ThreeState
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointType.StandardPanels
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy
import com.intellij.xdebugger.impl.breakpoints.XBreakpointTypeProxy
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointTypeProxy
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.swing.Icon

internal fun createFrontendXBreakpointType(
  project: Project,
  dto: XBreakpointTypeDto,
): XBreakpointTypeProxy {
  val lineTypeInfo = dto.lineTypeInfo
  return if (lineTypeInfo != null) {
    FrontendXLineBreakpointType(project, dto, lineTypeInfo)
  }
  else {
    FrontendXBreakpointType(project, dto)
  }
}

private class FrontendXLineBreakpointType(
  project: Project,
  dto: XBreakpointTypeDto,
  lineTypeInfo: XLineBreakpointTypeInfo,
) : FrontendXBreakpointType(project, dto), XLineBreakpointTypeProxy {
  override val temporaryIcon: Icon? = dto.icons.temporaryIcon?.icon()

  override val priority: Int = lineTypeInfo.priority

  override suspend fun canPutAt(editor: Editor, line: Int, project: Project): Boolean {
    val availableTypes = FrontendEditorLinesBreakpointsInfoManager.getInstance(project).getBreakpointsInfoForLine(editor, line).types
    return availableTypes.any { it.id == this@FrontendXLineBreakpointType.id }
  }

  override fun canPutAtFast(editor: Editor, line: Int, project: Project): ThreeState {
    val availableTypes = FrontendEditorLinesBreakpointsInfoManager.getInstance(project).getBreakpointsInfoForLineFast(editor, line)?.types
    if (availableTypes == null) {
      return ThreeState.UNSURE
    }
    return ThreeState.fromBoolean(availableTypes.any { it.id == this@FrontendXLineBreakpointType.id })
  }

  override suspend fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
    // TODO IJPL-185322 What if called for other editors?
    val editor = file.getOpenedEditor(project) ?: return false

    return canPutAt(editor, line, project)
  }

  override fun canPutAtFast(file: VirtualFile, line: Int, project: Project): ThreeState {
    // TODO IJPL-185322 What if called for other editors?
    val editor = file.getOpenedEditor(project) ?: return ThreeState.NO

    return canPutAtFast(editor, line, project)
  }

  private fun VirtualFile.getOpenedEditor(project: Project): Editor? {
    return FileEditorManager.getInstance(project).getEditors(this).firstNotNullOfOrNull {
      if (it is TextEditor) it.editor else null
    }
  }
}


private open class FrontendXBreakpointType(
  private val project: Project,
  private val dto: XBreakpointTypeDto,
) : XBreakpointTypeProxy {
  override val id: String = dto.id.id
  override val index: Int = dto.index
  override val title: String = dto.title

  override val enabledIcon: Icon = dto.icons.enabledIcon.icon()
  override val disabledIcon: Icon = dto.icons.disabledIcon.icon()
  override val suspendNoneIcon: Icon = dto.icons.suspendNoneIcon.icon()
  override val mutedEnabledIcon: Icon = dto.icons.mutedEnabledIcon.icon()
  override val mutedDisabledIcon: Icon = dto.icons.mutedDisabledIcon.icon()
  override val pendingIcon: Icon? = dto.icons.pendingIcon?.icon()
  override val inactiveDependentIcon: Icon = dto.icons.inactiveDependentIcon.icon()
  override val isSuspendThreadSupported: Boolean = dto.suspendThreadSupported

  // TODO: should we support changes from the backend (so we need to subscribe on them)
  private var _defaultSuspendPolicy = dto.defaultSuspendPolicy

  private val visibleStandardPanels: Set<StandardPanels> = dto.standardPanels.mapTo(mutableSetOf()) { it.standardPanel() }

  override val defaultSuspendPolicy: SuspendPolicy
    get() = _defaultSuspendPolicy

  override fun setDefaultSuspendPolicy(policy: SuspendPolicy) {
    _defaultSuspendPolicy = policy

    project.service<FrontendXBreakpointTypeProjectCoroutineScope>().cs.launch {
      XBreakpointApi.getInstance().setDefaultSuspendPolicy(project.projectId(), dto.id, policy)
    }
  }

  override fun getVisibleStandardPanels(): Set<StandardPanels> {
    return visibleStandardPanels
  }

  override fun createCustomPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>? {
    return null
  }

  override fun createCustomConditionsPanel(): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>? {
    return null
  }

  override fun createCustomRightPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>? {
    return null
  }

  override fun createCustomTopPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>? {
    return null
  }

  override fun isAddBreakpointButtonVisible(): Boolean {
    return dto.isAddBreakpointButtonVisible
  }

  override suspend fun addBreakpoint(project: Project): XBreakpointProxy? {
    val breakpointDto = XBreakpointTypeApi.getInstance().addBreakpointThroughLux(project.projectId(), dto.id).await() ?: return null
    return XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project).awaitBreakpointCreation(breakpointDto)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is FrontendXBreakpointType) return false

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }


}

@Service(Service.Level.PROJECT)
private class FrontendXBreakpointTypeProjectCoroutineScope(val cs: CoroutineScope)