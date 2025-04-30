// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem
import com.intellij.xdebugger.impl.rpc.XBreakpointDto
import com.sun.tools.javac.jvm.ByteCodes.breakpoint
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<XBreakpointManagerProxy>()

@ApiStatus.Internal
interface XBreakpointManagerProxy {
  val breakpointsDialogSettings: XBreakpointsDialogState?

  val allGroups: Set<String>

  val dependentBreakpointManager: XDependentBreakpointManagerProxy

  fun setBreakpointsDialogSettings(settings: XBreakpointsDialogState)

  fun setDefaultGroup(group: String)

  fun addBreakpoint(breakpointDto: XBreakpointDto): XBreakpointProxy?

  fun getAllBreakpointItems(): List<BreakpointItem>

  fun getLineBreakpointManager(): XLineBreakpointManager

  fun getAllBreakpointTypes(): List<XBreakpointTypeProxy>
  fun getLineBreakpointTypes(): List<XLineBreakpointTypeProxy>

  fun subscribeOnBreakpointsChanges(disposable: Disposable, listener: () -> Unit)
  fun getLastRemovedBreakpoint(): XBreakpointProxy?

  fun removeBreakpoint(breakpoint: XBreakpointProxy)
  fun removeBreakpoints(breakpoints: Collection<XBreakpointProxy>)

  fun findBreakpointAtLine(type: XLineBreakpointTypeProxy, file: VirtualFile, line: Int): XLineBreakpointProxy? =
    findBreakpointsAtLine(type, file, line).firstOrNull()
  fun findBreakpointsAtLine(type: XLineBreakpointTypeProxy, file: VirtualFile, line: Int): List<XLineBreakpointProxy>

  class Monolith(val breakpointManager: XBreakpointManagerImpl) : XBreakpointManagerProxy {
    override val breakpointsDialogSettings: XBreakpointsDialogState?
      get() = breakpointManager.breakpointsDialogSettings

    override val allGroups: Set<String>
      get() = breakpointManager.allGroups

    override val dependentBreakpointManager: XDependentBreakpointManagerProxy
      get() = XDependentBreakpointManagerProxy.Monolith(breakpointManager.dependentBreakpointManager)

    override fun setBreakpointsDialogSettings(settings: XBreakpointsDialogState) {
      breakpointManager.breakpointsDialogSettings = settings
    }

    override fun setDefaultGroup(group: String) {
      breakpointManager.defaultGroup = group
    }

    override fun addBreakpoint(breakpointDto: XBreakpointDto): XBreakpointProxy? {
      LOG.error("addBreakpoint with Dto should not be called for monolith")
      return null
    }

    override fun getAllBreakpointItems(): List<BreakpointItem> {
      val breakpointManager = XDebuggerManager.getInstance(breakpointManager.project).getBreakpointManager() as XBreakpointManagerImpl
      return breakpointManager.allBreakpoints.map {
        XBreakpointItem(it, this)
      }
    }

    override fun getLineBreakpointManager(): XLineBreakpointManager {
      return breakpointManager.lineBreakpointManager
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

    override fun removeBreakpoints(breakpoints: Collection<XBreakpointProxy>) {
      val monolithBreakpoints = breakpoints.filterIsInstance<XBreakpointProxy.Monolith>().map { it.breakpoint }
      if (monolithBreakpoints.isEmpty()) {
        return
      }
      breakpointManager.removeBreakpoints(monolithBreakpoints)
    }

    override fun findBreakpointAtLine(type: XLineBreakpointTypeProxy, file: VirtualFile, line: Int): XLineBreakpointProxy? {
      val breakpoint = breakpointManager.findBreakpointAtLine((type as XLineBreakpointTypeProxy.Monolith).breakpointType, file, line)
      if (breakpoint is XLineBreakpointImpl<*>) {
        return breakpoint.asProxy()
      }
      return null
    }

    override fun findBreakpointsAtLine(type: XLineBreakpointTypeProxy, file: VirtualFile, line: Int): List<XLineBreakpointProxy> {
      return breakpointManager.findBreakpointsAtLine((type as XLineBreakpointTypeProxy.Monolith).breakpointType, file, line).map { it.asProxy() }
    }
  }
}