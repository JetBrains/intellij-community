// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.impl.breakpoints.XBreakpointItem
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerProxy
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy
import com.intellij.xdebugger.impl.breakpoints.XBreakpointsDialogState
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem
import com.intellij.xdebugger.impl.rpc.XDebuggerManagerApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

internal class FrontendXBreakpointManager(private val project: Project, private val cs: CoroutineScope) : XBreakpointManagerProxy {
  private val breakpointsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  val breakpoints: StateFlow<Set<XBreakpointProxy>> = channelFlow {
    XDebuggerManagerApi.getInstance().getBreakpoints(project.projectId()).collectLatest { breakpointDtos ->
      // TODO: do we need to reuse breakpoint coroutine scopes?
      supervisorScope {
        val breakpointProxies = breakpointDtos.mapTo(mutableSetOf()) {
          FrontendXBreakpointProxy(
            project = project,
            cs = this,
            dto = it,
            onBreakpointChange = {
              breakpointsChanged.tryEmit(Unit)
            }
          )
        }
        send(breakpointProxies)
        breakpointsChanged.tryEmit(Unit)
      }
      awaitCancellation()
    }
  }.stateIn(cs, SharingStarted.Eagerly, emptySet())

  override val breakpointsDialogSettings: XBreakpointsDialogState?
    get() = null // TODO: add persistance
  override val allGroups: Set<String>
    get() = setOf() // TODO: implement groups

  override fun setBreakpointsDialogSettings(settings: XBreakpointsDialogState) {
    // TODO: add persistance
  }

  override fun setDefaultGroup(group: String) {
    // TODO: implement groups
  }

  override fun getAllBreakpointItems(): List<BreakpointItem> {
    return breakpoints.value.map { proxy ->
      XBreakpointItem(proxy, this)
    }
  }

  override fun getAllBreakpointTypes(): List<XBreakpointType<*, *>> {
    return listOf() // TODO: implement breakpoint types
  }

  override fun subscribeOnBreakpointsChanges(disposable: Disposable, listener: () -> Unit) {
    val scope = cs.childScope("BreakpointsChangesListener")
    val childDisposable = Disposable { scope.cancel("disposed") }
    Disposer.register(disposable, childDisposable)
    scope.launch(Dispatchers.EDT) {
      breakpointsChanged.collect {
        listener()
      }
    }
  }
}