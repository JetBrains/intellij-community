// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil.breakpointTypes
import com.intellij.xdebugger.impl.rpc.XBreakpointTypeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.*
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

  open class Monolith @Deprecated("Use type.asProxy() instead") internal constructor(
    val project: Project,
    initBreakpointType: XBreakpointType<*, *>,
  ) : XBreakpointTypeProxy {
    open val breakpointType: XBreakpointType<*, *> = initBreakpointType
    private val defaultState: BreakpointState
      get() = (XDebuggerManager.getInstance(project).breakpointManager as XBreakpointManagerImpl).getBreakpointDefaults(breakpointType)

    override val id: String get() = breakpointType.id

    override val index: Int
      get() = breakpointTypes().indexOf(breakpointType).orElse(-1).toInt()
    override val title: String
      get() = breakpointType.title
    override val enabledIcon: Icon
      get() = breakpointType.enabledIcon
    override val disabledIcon: Icon
      get() = breakpointType.disabledIcon
    override val suspendNoneIcon: Icon
      get() = breakpointType.suspendNoneIcon
    override val mutedEnabledIcon: Icon
      get() = breakpointType.mutedEnabledIcon
    override val mutedDisabledIcon: Icon
      get() = breakpointType.mutedDisabledIcon
    override val pendingIcon: Icon?
      get() = breakpointType.pendingIcon
    override val inactiveDependentIcon: Icon
      get() = breakpointType.inactiveDependentIcon
    override val isSuspendThreadSupported: Boolean
      get() = breakpointType.isSuspendThreadSupported

    override val defaultSuspendPolicy: SuspendPolicy
      get() = defaultState.suspendPolicy

    override fun setDefaultSuspendPolicy(policy: SuspendPolicy) {
      defaultState.suspendPolicy = policy
    }

    override fun getVisibleStandardPanels(): EnumSet<XBreakpointType.StandardPanels> = breakpointType.visibleStandardPanels

    @Suppress("UNCHECKED_CAST")
    override fun createCustomPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>? {
      return breakpointType.createCustomPropertiesPanel(project) as? XBreakpointCustomPropertiesPanel<XBreakpoint<*>>
    }

    @Suppress("UNCHECKED_CAST")
    override fun createCustomConditionsPanel(): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>? {
      return breakpointType.createCustomConditionsPanel() as? XBreakpointCustomPropertiesPanel<XBreakpoint<*>>
    }

    @Suppress("UNCHECKED_CAST")
    override fun createCustomRightPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>? {
      return breakpointType.createCustomRightPropertiesPanel(project) as? XBreakpointCustomPropertiesPanel<XBreakpoint<*>>
    }

    @Suppress("UNCHECKED_CAST")
    override fun createCustomTopPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XBreakpoint<*>>? {
      return breakpointType.createCustomTopPropertiesPanel(project) as? XBreakpointCustomPropertiesPanel<XBreakpoint<*>>
    }

    override fun isAddBreakpointButtonVisible(): Boolean {
      return breakpointType.isAddBreakpointButtonVisible
    }

    override suspend fun addBreakpoint(project: Project): XBreakpointProxy? {
      val breakpoint = withContext(Dispatchers.EDT) {
        breakpointType.addBreakpoint(project, null)
      }
      return (breakpoint as? XBreakpointBase<*, *, *>)?.asProxy()
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Monolith) return false

      return breakpointType == other.breakpointType
    }

    override fun hashCode(): Int {
      return breakpointType.hashCode()
    }
  }
}


@Suppress("DEPRECATION")
@ApiStatus.Internal
fun <T : XBreakpointType<*, *>> T.asProxy(project: Project): XBreakpointTypeProxy {
  return if (this is XLineBreakpointType<*>) {
    this.asProxy(project)
  }
  else {
    XBreakpointTypeProxy.Monolith(project, this)
  }
}

@Suppress("DEPRECATION")
@ApiStatus.Internal
fun <T : XLineBreakpointType<*>> T.asProxy(project: Project): XLineBreakpointTypeProxy = XLineBreakpointTypeProxy.Monolith(project, this)
