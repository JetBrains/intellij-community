// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.util.SequentialRpcRequestsExecutor
import com.intellij.platform.debugger.impl.rpc.XBreakpointDependencyDto
import com.intellij.platform.debugger.impl.rpc.XBreakpointDependencyEvent
import com.intellij.platform.debugger.impl.rpc.XBreakpointId
import com.intellij.platform.debugger.impl.rpc.XDependentBreakpointManagerApi
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDependentBreakpointManagerProxy
import com.intellij.platform.project.projectId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class FrontendXDependentBreakpointManagerProxy(
  private val project: Project,
  cs: CoroutineScope,
  private val breakpointById: (XBreakpointId) -> XBreakpointProxy?,
  private val onDependencyChanged: (XBreakpointId) -> Unit,
) : XDependentBreakpointManagerProxy {
  private val dependantBreakpoints = mutableMapOf<XBreakpointId, XBreakpointDependencyDto>()
  private val sequentialExecutor = SequentialRpcRequestsExecutor.create(cs)

  init {
    cs.launch {
      durableWithStateReset(block = {
        val breakpointsDependencies = XDependentBreakpointManagerApi.getInstance().breakpointDependencies(project.projectId())

        for (dto in breakpointsDependencies.initialDependencies) {
          updateDependency(dto)
        }

        breakpointsDependencies.dependencyEvents.toFlow().collect {
          when (it) {
            is XBreakpointDependencyEvent.Add -> {
              updateDependency(it.dependency)
            }
            is XBreakpointDependencyEvent.Remove -> {
              removeDependency(it.child)
            }
          }
        }
      }, stateReset = {
        val affectedBreakpoints = dependantBreakpoints.keys.toList()
        dependantBreakpoints.clear()
        affectedBreakpoints.forEach(onDependencyChanged)
      })
    }
  }

  override fun getMasterBreakpoint(breakpoint: XBreakpointProxy): XBreakpointProxy? {
    return dependantBreakpoints[breakpoint.id]?.parent?.let { breakpointById(it) }
  }

  override fun isLeaveEnabled(breakpoint: XBreakpointProxy): Boolean {
    return dependantBreakpoints[breakpoint.id]?.isLeaveEnabled == true
  }

  override fun clearMasterBreakpoint(breakpoint: XBreakpointProxy) {
    removeDependency(breakpoint.id)

    sequentialExecutor.execute {
      XDependentBreakpointManagerApi.getInstance().clearMasterBreakpoint(breakpoint.id)
    }
  }

  override fun setMasterBreakpoint(breakpoint: XBreakpointProxy, masterBreakpoint: XBreakpointProxy, selected: Boolean) {
    updateDependency(XBreakpointDependencyDto(
      child = breakpoint.id,
      parent = masterBreakpoint.id,
      isLeaveEnabled = selected,
    ))
    sequentialExecutor.execute {
      XDependentBreakpointManagerApi.getInstance().setMasterDependency(breakpoint.id, masterBreakpoint.id, selected)
    }
  }

  private fun updateDependency(dependency: XBreakpointDependencyDto) {
    if (dependantBreakpoints.put(dependency.child, dependency) != dependency) {
      onDependencyChanged(dependency.child)
    }
  }

  private fun removeDependency(breakpointId: XBreakpointId) {
    if (dependantBreakpoints.remove(breakpointId) != null) {
      onDependencyChanged(breakpointId)
    }
  }
}
