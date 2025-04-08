// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.impl.breakpoints.XBreakpointItem
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerProxy
import com.intellij.xdebugger.impl.breakpoints.XBreakpointsDialogState
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem
import com.intellij.xdebugger.impl.rpc.XBreakpointDto
import com.intellij.xdebugger.impl.rpc.XDebuggerManagerApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

internal class FrontendXBreakpointManager(private val project: Project, private val cs: CoroutineScope) : XBreakpointManagerProxy {
  val breakpoints: StateFlow<Set<XBreakpointDto>> = channelFlow {
    XDebuggerManagerApi.getInstance().getBreakpoints(project.projectId()).collectLatest { breakpointDtos ->
      send(breakpointDtos)
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
    return breakpoints.value.map { dto ->
      XBreakpointItem(FrontendXBreakpointProxy(project, dto), this)
    }
  }

  override fun getAllBreakpointTypes(): List<XBreakpointType<*, *>> {
    return listOf() // TODO: implement breakpoint types
  }
}