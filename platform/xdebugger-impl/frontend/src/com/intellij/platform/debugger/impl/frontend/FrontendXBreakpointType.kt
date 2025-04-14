// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointType.StandardPanels
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel
import com.intellij.xdebugger.impl.breakpoints.XBreakpointTypeProxy
import com.intellij.xdebugger.impl.rpc.XBreakpointApi
import com.intellij.xdebugger.impl.rpc.XBreakpointTypeDto
import com.intellij.xdebugger.impl.rpc.standardPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.swing.Icon

internal class FrontendXBreakpointType(
  private val project: Project,
  private val dto: XBreakpointTypeDto,
) : XBreakpointTypeProxy {
  override val id: String = dto.id.id
  override val index: Int = dto.index
  override val title: String = dto.title
  override val enabledIcon: Icon = dto.enabledIcon.icon()
  override val isLineBreakpoint: Boolean = dto.lineTypeInfo != null
  override val isSuspendThreadSupported: Boolean = dto.suspendThreadSupported
  override val priority: Int? = dto.lineTypeInfo?.priority

  // TODO: should we support changes from the backend (so we need to subscribe on them)
  private var _defaultSuspendPolicy = dto.defaultSuspendPolicy

  private val visibleStandardPanels: Set<StandardPanels> = dto.standardPanels.mapTo(mutableSetOf()) { it.standardPanel() }.also {
    // TODO: support DEPENDENCY on the frontend side
    //  see com.intellij.platform.debugger.impl.frontend.FrontendXDependentBreakpointManagerProxy
    it.remove(StandardPanels.DEPENDENCY)
  }

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
    // TODO: LUXify?
    return null
  }

  override fun createCustomConditionsPanel(): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>? {
    // TODO: LUXify?
    return null
  }

  override fun createCustomRightPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>? {
    // TODO: LUXify?
    return null
  }

  override fun createCustomTopPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>? {
    // TODO: LUXify?
    return null
  }
}

@Service(Service.Level.PROJECT)
private class FrontendXBreakpointTypeProjectCoroutineScope(val cs: CoroutineScope)