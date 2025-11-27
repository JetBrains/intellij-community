// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared.proxy

import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XBreakpointTypeId
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
interface XBreakpointTypeProxy {
  val id: String
  val index: Int
  val title: String
  val enabledIcon: Icon
  val disabledIcon: Icon
  val suspendNoneIcon: Icon
  val mutedEnabledIcon: Icon
  val mutedDisabledIcon: Icon
  val pendingIcon: Icon?
  val inactiveDependentIcon: Icon

  val isSuspendThreadSupported: Boolean

  val defaultSuspendPolicy: SuspendPolicy

  val typeId: XBreakpointTypeId get() = XBreakpointTypeId(id)

  fun setDefaultSuspendPolicy(policy: SuspendPolicy)
  fun getVisibleStandardPanels(): Set<XBreakpointType.StandardPanels>
  fun createCustomPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?
  fun createCustomConditionsPanel(): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?
  fun createCustomRightPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?
  fun createCustomTopPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?
  fun isAddBreakpointButtonVisible(): Boolean
  suspend fun addBreakpoint(project: Project): XBreakpointProxy?
}