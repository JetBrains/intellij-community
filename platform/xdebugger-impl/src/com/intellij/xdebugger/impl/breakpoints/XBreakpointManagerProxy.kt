// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
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

  fun getAllBreakpointTypes(): List<XBreakpointTypeProxy>
  fun getLineBreakpointTypes(): List<XLineBreakpointTypeProxy>

  fun subscribeOnBreakpointsChanges(disposable: Disposable, listener: () -> Unit)
  fun getLastRemovedBreakpoint(): XBreakpointProxy?

  fun removeBreakpoint(breakpoint: XBreakpointProxy)
  fun findBreakpointAtLine(type: XBreakpointTypeProxy, file: VirtualFile, line: Int): XLineBreakpoint<*>?

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

    override fun getAllBreakpointTypes(): List<XBreakpointTypeProxy> {
      return XBreakpointUtil.breakpointTypes().map { it.asProxy(breakpointManager.project) }.toList()
    }

    override fun getLineBreakpointTypes(): List<XLineBreakpointTypeProxy> {
      return XDebuggerUtil.getInstance().lineBreakpointTypes.map { it.asProxy(breakpointManager.project) }.toList()
    }

    override fun subscribeOnBreakpointsChanges(disposable: Disposable, listener: () -> Unit) {
      XBreakpointUtil.subscribeOnBreakpointsChanges(breakpointManager.project, disposable, onBreakpointChange = {
        listener()
      })
    }

    override fun getLastRemovedBreakpoint(): XBreakpointProxy? {
      val lastRemovedBreakpoint = breakpointManager.lastRemovedBreakpoint
      if (lastRemovedBreakpoint !is XBreakpointBase<*, *, *>) {
        return null
      }
      return lastRemovedBreakpoint.asProxy()
    }

    override fun removeBreakpoint(breakpoint: XBreakpointProxy) {
      if (breakpoint !is XBreakpointProxy.Monolith) {
        return
      }
      breakpointManager.removeBreakpoint(breakpoint.breakpoint)
    }

    override fun findBreakpointAtLine(type: XBreakpointTypeProxy, file: VirtualFile, line: Int): XLineBreakpoint<*>? {
      return breakpointManager.findBreakpointAtLine((type as XBreakpointTypeProxy.Monolith).breakpointType as XLineBreakpointType<*>, file, line)
    }
  }
}