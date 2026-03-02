// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.proxy

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.rpc.XBreakpointId
import com.intellij.platform.debugger.impl.shared.InlineBreakpointsCache
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointTypeProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDependentBreakpointManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointInstallationInfo
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointTypeProxy
import com.intellij.util.ThrowableRunnable
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XBreakpointItem
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.intellij.xdebugger.impl.breakpoints.XBreakpointsDialogState
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointManager
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem
import java.util.concurrent.CompletableFuture

private class MonolithBreakpointManager(val breakpointManager: XBreakpointManagerImpl) : XBreakpointManagerProxy {
  override val breakpointsDialogSettings: XBreakpointsDialogState?
    get() = breakpointManager.breakpointsDialogSettings

  override val dependentBreakpointManager: XDependentBreakpointManagerProxy
    get() = MonolithDependentBreakpointManagerProxy(breakpointManager.dependentBreakpointManager)

  override val inlineBreakpointsCache: InlineBreakpointsCache get() = MonolithInlineBreakpointsCache(breakpointManager.project)

  override fun setBreakpointsDialogSettings(settings: XBreakpointsDialogState) {
    breakpointManager.breakpointsDialogSettings = settings
  }

  override fun getDefaultGroup(): String? {
    return breakpointManager.defaultGroup
  }

  override fun setDefaultGroup(group: String?) {
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

  override fun getAllBreakpoints(): List<XBreakpointProxy> {
    return breakpointManager.allBreakpoints.map { it.asProxy() }
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
    breakpointManager.project.getMessageBus().connect(disposable).subscribe(XBreakpointListener.TOPIC, object : XBreakpointListener<XBreakpoint<*>> {
      override fun breakpointAdded(breakpoint: XBreakpoint<*>) {
        listener()
      }

      override fun breakpointChanged(breakpoint: XBreakpoint<*>) {
        listener()
      }

      override fun breakpointRemoved(breakpoint: XBreakpoint<*>) {
        listener()
      }
    })
  }

  override fun getLastRemovedBreakpoint(): XBreakpointProxy? {
    val lastRemovedBreakpoint = breakpointManager.lastRemovedBreakpoint
    if (lastRemovedBreakpoint !is XBreakpointBase<*, *, *>) {
      return null
    }
    return lastRemovedBreakpoint.asProxy()
  }

  override fun removeBreakpoint(breakpoint: XBreakpointProxy): CompletableFuture<Void?> {
    if (breakpoint !is MonolithBreakpointProxy) {
      return CompletableFuture.completedFuture(null)
    }
    breakpointManager.removeBreakpoint(breakpoint.breakpoint)
    return CompletableFuture.completedFuture(null)
  }

  override fun restoreRemovedBreakpoint(breakpoint: XBreakpointProxy) {
    if (breakpoint !is MonolithBreakpointProxy) {
      return
    }
    WriteAction.run<RuntimeException?>(ThrowableRunnable {
      breakpointManager.restoreLastRemovedBreakpoint()
    })
  }

  override fun copyLineBreakpoint(breakpoint: XLineBreakpointProxy, file: VirtualFile, line: Int) {
    if (breakpoint !is MonolithLineBreakpointProxy) {
      return
    }
    breakpointManager.copyLineBreakpoint(breakpoint.breakpoint, file.url, line)
  }

  override fun onBreakpointRemoval(breakpoint: XLineBreakpointProxy, session: XDebugSessionProxy) {
    if (breakpoint !is MonolithLineBreakpointProxy) return
    val monolithSession = MonolithXDebugManagerProxy.findSessionImpl(session)
    monolithSession.checkActiveNonLineBreakpointOnRemoval(breakpoint.breakpoint)
  }

  override fun rememberRemovedBreakpoint(breakpoint: XBreakpointProxy) {
    if (breakpoint !is MonolithBreakpointProxy) {
      return
    }
    breakpointManager.rememberRemovedBreakpoint(breakpoint.breakpoint)
  }

  override fun findBreakpointAtLine(type: XLineBreakpointTypeProxy, file: VirtualFile, line: Int): XLineBreakpointProxy? {
    val breakpoint = breakpointManager.findBreakpointAtLine((type as MonolithLineBreakpointTypeProxy).breakpointType, file, line)
    if (breakpoint is XLineBreakpointImpl<*>) {
      return breakpoint.asProxy()
    }
    return null
  }

  override fun findBreakpointsAtLine(type: XLineBreakpointTypeProxy, file: VirtualFile, line: Int): List<XLineBreakpointProxy> {
    val breakpointType = (type as MonolithLineBreakpointTypeProxy).breakpointType
    return breakpointManager.findBreakpointsAtLine(breakpointType, file, line)
      .filterIsInstance<XLineBreakpointImpl<*>>()
      .map { it.asProxy() }
  }

  override suspend fun <T> withLightBreakpointIfPossible(editor: Editor?, info: XLineBreakpointInstallationInfo, block: suspend () -> T): T {
    return block()
  }
}

internal fun XBreakpointManagerImpl.asProxy(): XBreakpointManagerProxy = MonolithBreakpointManager(this)
