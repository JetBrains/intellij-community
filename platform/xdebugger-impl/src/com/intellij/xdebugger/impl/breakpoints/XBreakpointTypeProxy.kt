// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil.breakpointTypes
import org.jetbrains.annotations.ApiStatus
import java.util.*
import javax.swing.Icon

@ApiStatus.Internal
interface XBreakpointTypeProxy {
  val id: String
  val index: Int
  val title: String
  val enabledIcon: Icon
  val isLineBreakpoint: Boolean

  val isSuspendThreadSupported: Boolean
  val priority: Int?

  val defaultSuspendPolicy: SuspendPolicy

  fun setDefaultSuspendPolicy(policy: SuspendPolicy)
  fun getVisibleStandardPanels(): EnumSet<XBreakpointType.StandardPanels>
  fun createCustomPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?
  fun createCustomConditionsPanel(): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?
  fun createCustomRightPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?
  fun createCustomTopPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?

  class Monolith(
    val project: Project,
    val breakpointType: XBreakpointType<*, *>,
  ) : XBreakpointTypeProxy {
    private val defaultState = (XDebuggerManager.getInstance(project).breakpointManager as XBreakpointManagerImpl).getBreakpointDefaults(breakpointType)

    override val id: String
      get() = breakpointType.id
    override val index: Int
      get() = breakpointTypes().indexOf(breakpointType).orElse(-1).toInt()
    override val title: String
      get() = breakpointType.title
    override val enabledIcon: Icon
      get() = breakpointType.enabledIcon
    override val isLineBreakpoint: Boolean
      get() = breakpointType is XLineBreakpointType<*>
    override val isSuspendThreadSupported: Boolean
      get() = breakpointType.isSuspendThreadSupported

    override val priority: Int?
      get() = if (breakpointType is XLineBreakpointType<*>) {
        breakpointType.priority
      }
      else {
        null
      }

    override val defaultSuspendPolicy: SuspendPolicy
      get() = defaultState.suspendPolicy

    override fun setDefaultSuspendPolicy(policy: SuspendPolicy) {
      defaultState.suspendPolicy = policy
    }

    override fun getVisibleStandardPanels(): EnumSet<XBreakpointType.StandardPanels> = breakpointType.visibleStandardPanels

    override fun createCustomPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>? {
      return breakpointType.createCustomPropertiesPanel(project) as? XBreakpointCustomPropertiesPanel<XBreakpoint<*>>
    }

    override fun createCustomConditionsPanel(): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>? {
      return breakpointType.createCustomConditionsPanel() as? XBreakpointCustomPropertiesPanel<XBreakpoint<*>>
    }

    override fun createCustomRightPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>? {
      return breakpointType.createCustomRightPropertiesPanel(project) as? XBreakpointCustomPropertiesPanel<XBreakpoint<*>>
    }

    override fun createCustomTopPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>? {
      return breakpointType.createCustomTopPropertiesPanel(project) as? XBreakpointCustomPropertiesPanel<XBreakpoint<*>>
    }
  }
}