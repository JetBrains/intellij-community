// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ThrowableRunnable
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.impl.XLineBreakpointInstallationInfo
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem
import com.intellij.xdebugger.impl.rpc.XBreakpointId
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<XBreakpointManagerProxy>()

@ApiStatus.Internal
interface XBreakpointManagerProxy {
  val breakpointsDialogSettings: XBreakpointsDialogState?

  val dependentBreakpointManager: XDependentBreakpointManagerProxy

  fun setBreakpointsDialogSettings(settings: XBreakpointsDialogState)

  fun setDefaultGroup(group: String)

  suspend fun awaitBreakpointCreation(breakpointId: XBreakpointId): XBreakpointProxy?

  fun getAllBreakpointItems(): List<BreakpointItem>

  fun getLineBreakpointManager(): XLineBreakpointManager

  fun getAllBreakpointTypes(): List<XBreakpointTypeProxy>
  fun getLineBreakpointTypes(): List<XLineBreakpointTypeProxy>

  fun subscribeOnBreakpointsChanges(disposable: Disposable, listener: () -> Unit)
  fun getLastRemovedBreakpoint(): XBreakpointProxy?

  fun removeBreakpoint(breakpoint: XBreakpointProxy)

  fun rememberRemovedBreakpoint(breakpoint: XBreakpointProxy)
  fun restoreRemovedBreakpoint(breakpoint: XBreakpointProxy)

  fun findBreakpointAtLine(type: XLineBreakpointTypeProxy, file: VirtualFile, line: Int): XLineBreakpointProxy? =
    findBreakpointsAtLine(type, file, line).firstOrNull()

  fun findBreakpointsAtLine(type: XLineBreakpointTypeProxy, file: VirtualFile, line: Int): List<XLineBreakpointProxy>

  suspend fun <T> withLightBreakpointIfPossible(editor: Editor?, info: XLineBreakpointInstallationInfo, block: suspend () -> T): T

  class Monolith(val breakpointManager: XBreakpointManagerImpl) : XBreakpointManagerProxy {
    override val breakpointsDialogSettings: XBreakpointsDialogState?
      get() = breakpointManager.breakpointsDialogSettings

    override val dependentBreakpointManager: XDependentBreakpointManagerProxy
      get() = XDependentBreakpointManagerProxy.Monolith(breakpointManager.dependentBreakpointManager)

    override fun setBreakpointsDialogSettings(settings: XBreakpointsDialogState) {
      breakpointManager.breakpointsDialogSettings = settings
    }

    override fun setDefaultGroup(group: String) {
      breakpointManager.defaultGroup = group
    }

    override suspend fun awaitBreakpointCreation(breakpointId: XBreakpointId): XBreakpointProxy? {
      return breakpointManager.allBreakpoints.firstOrNull { it.breakpointId == breakpointId }?.asProxy()
    }

    override fun getAllBreakpointItems(): List<BreakpointItem> {
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

    override fun restoreRemovedBreakpoint(breakpoint: XBreakpointProxy) {
      if (breakpoint !is XBreakpointProxy.Monolith) {
        return
      }
      WriteAction.run<RuntimeException?>(ThrowableRunnable {
        breakpointManager.restoreLastRemovedBreakpoint()
      })
    }

    override fun rememberRemovedBreakpoint(breakpoint: XBreakpointProxy) {
      if (breakpoint !is XBreakpointProxy.Monolith) {
        return
      }
      breakpointManager.rememberRemovedBreakpoint(breakpoint.breakpoint)
    }

    override fun findBreakpointAtLine(type: XLineBreakpointTypeProxy, file: VirtualFile, line: Int): XLineBreakpointProxy? {
      val breakpoint = breakpointManager.findBreakpointAtLine((type as XLineBreakpointTypeProxy.Monolith).breakpointType, file, line)
      if (breakpoint is XLineBreakpointImpl<*>) {
        return breakpoint.asProxy()
      }
      return null
    }

    override fun findBreakpointsAtLine(type: XLineBreakpointTypeProxy, file: VirtualFile, line: Int): List<XLineBreakpointProxy> {
      val breakpointType = (type as XLineBreakpointTypeProxy.Monolith).breakpointType
      return breakpointManager.findBreakpointsAtLine(breakpointType, file, line)
        .filterIsInstance<XLineBreakpointImpl<*>>()
        .map { it.asProxy() }
    }

    override suspend fun <T> withLightBreakpointIfPossible(editor: Editor?, info: XLineBreakpointInstallationInfo, block: suspend () -> T): T {
      return block()
    }
  }
}