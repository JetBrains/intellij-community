// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy
import com.intellij.xdebugger.impl.breakpoints.XDependentBreakpointManagerProxy
import com.intellij.xdebugger.impl.rpc.XBreakpointDependencyDto
import com.intellij.xdebugger.impl.rpc.XBreakpointDependencyEvent
import com.intellij.xdebugger.impl.rpc.XBreakpointId
import com.intellij.xdebugger.impl.rpc.XDependentBreakpointManagerApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal class FrontendXDependentBreakpointManagerProxy(
  private val project: Project,
  private val cs: CoroutineScope,
  private val breakpointById: (XBreakpointId) -> XBreakpointProxy?,
) : XDependentBreakpointManagerProxy {
  private val dependantBreakpoints = mutableMapOf<XBreakpointId, XBreakpointDependencyDto>()

  private val sequencedRequests = Channel<suspend () -> Unit>(capacity = Channel.UNLIMITED)

  init {
    cs.launch {
      val breakpointsDependencies = XDependentBreakpointManagerApi.getInstance().breakpointDependencies(project.projectId())
      for (dto in breakpointsDependencies.initialDependencies) {
        dependantBreakpoints[dto.child] = dto
      }

      breakpointsDependencies.dependencyEvents.toFlow().collect {
        when (it) {
          is XBreakpointDependencyEvent.Add -> {
            dependantBreakpoints[it.dependency.child] = it.dependency
          }
          is XBreakpointDependencyEvent.Remove -> {
            dependantBreakpoints.remove(it.child)
          }
        }
      }
    }

    cs.launch {
      for (rpcRequest in sequencedRequests) {
        rpcRequest()
      }
    }
  }

  override fun getMasterBreakpoint(breakpoint: XBreakpointProxy): XBreakpointProxy? {
    return dependantBreakpoints[breakpoint.id]?.parent?.let { breakpointById(it) }
  }

  override fun isLeaveEnabled(breakpoint: XBreakpointProxy): Boolean {
    return dependantBreakpoints[breakpoint.id]?.isLeaveEnabled == true
  }

  override fun clearMasterBreakpoint(breakpoint: XBreakpointProxy) {
    dependantBreakpoints.remove(breakpoint.id)

    sequencedRequests.trySend {
      XDependentBreakpointManagerApi.getInstance().clearMasterBreakpoint(breakpoint.id)
    }
  }

  override fun setMasterBreakpoint(breakpoint: XBreakpointProxy, masterBreakpoint: XBreakpointProxy, selected: Boolean) {
    dependantBreakpoints[breakpoint.id] = XBreakpointDependencyDto(
      child = breakpoint.id,
      parent = masterBreakpoint.id,
      isLeaveEnabled = selected,
    )
    sequencedRequests.trySend {
      XDependentBreakpointManagerApi.getInstance().setMasterDependency(breakpoint.id, masterBreakpoint.id, selected)
    }
  }
}