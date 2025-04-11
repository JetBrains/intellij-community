// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.Disposable
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XBreakpointManagerProxy {
  val breakpointsDialogSettings: XBreakpointsDialogState?

  val allGroups: Set<String>

  val dependentBreakpointManager: XDependentBreakpointManagerProxy

  fun setBreakpointsDialogSettings(settings: XBreakpointsDialogState)

  fun setDefaultGroup(group: String)

  fun getAllBreakpointItems(): List<BreakpointItem>

  fun getAllBreakpointTypes(): List<XBreakpointType<*, *>>

  fun subscribeOnBreakpointsChanges(disposable: Disposable, listener: () -> Unit)
  fun getLastRemovedBreakpoint(): XBreakpointProxy?

  class Monolith(val breakpointManager: XBreakpointManagerImpl) : XBreakpointManagerProxy {
    override val breakpointsDialogSettings: XBreakpointsDialogState?
      get() = breakpointManager.breakpointsDialogSettings

    override val allGroups: Set<String>
      get() = breakpointManager.allGroups

    override val dependentBreakpointManager: XDependentBreakpointManagerProxy = XDependentBreakpointManagerProxy.Monolith(breakpointManager.dependentBreakpointManager)

    override fun setBreakpointsDialogSettings(settings: XBreakpointsDialogState) {
      breakpointManager.breakpointsDialogSettings = settings
    }

    override fun setDefaultGroup(group: String) {
      breakpointManager.defaultGroup = group
    }

    override fun getAllBreakpointItems(): List<BreakpointItem> {
      val breakpointManager = XDebuggerManager.getInstance(breakpointManager.project).getBreakpointManager() as XBreakpointManagerImpl
      return breakpointManager.allBreakpoints.map {
        XBreakpointItem(it, this)
      }
    }

    override fun getAllBreakpointTypes(): List<XBreakpointType<*, *>> {
      return XBreakpointUtil.breakpointTypes().toList()
    }

    override fun subscribeOnBreakpointsChanges(disposable: Disposable, listener: () -> Unit) {
      XBreakpointUtil.subscribeOnBreakpointsChanges(breakpointManager.project, disposable, onBreakpointChange = {
        listener()
      })
    }

    override fun getLastRemovedBreakpoint(): XBreakpointProxy? = XBreakpointProxy.Monolith(breakpointManager.lastRemovedBreakpoint as XBreakpointBase<*, *, *>)
  }
}