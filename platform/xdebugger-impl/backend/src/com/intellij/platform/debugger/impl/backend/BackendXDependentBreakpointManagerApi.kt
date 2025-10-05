// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XBreakpointDependenciesDto
import com.intellij.platform.debugger.impl.rpc.XBreakpointDependencyDto
import com.intellij.platform.debugger.impl.rpc.XBreakpointDependencyEvent
import com.intellij.platform.debugger.impl.rpc.XDependentBreakpointManagerApi
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XDependentBreakpointListener
import com.intellij.xdebugger.impl.breakpoints.XDependentBreakpointManager
import com.intellij.xdebugger.impl.rpc.XBreakpointId
import com.intellij.xdebugger.impl.rpc.models.findValue
import fleet.rpc.core.toRpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

internal class BackendXDependentBreakpointManagerApi : XDependentBreakpointManagerApi {
  override suspend fun breakpointDependencies(projectId: ProjectId): XBreakpointDependenciesDto {
    val project = projectId.findProject()
    val dependentBreakpointManager = (XDebuggerManager.getInstance(project) as XDebuggerManagerImpl).breakpointManager.dependentBreakpointManager

    val dependencyEvents = createDependencyEventsFlow(project, dependentBreakpointManager)

    val initialDependencies = collectInitialDependencies(dependentBreakpointManager)

    return XBreakpointDependenciesDto(
      initialDependencies = initialDependencies,
      dependencyEvents = dependencyEvents.toRpc()
    )
  }

  override suspend fun clearMasterBreakpoint(breakpointId: XBreakpointId) {
    val breakpoint = breakpointId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      val dependentBreakpointManager = (XDebuggerManager.getInstance(breakpoint.project) as XDebuggerManagerImpl).breakpointManager.dependentBreakpointManager
      dependentBreakpointManager.clearMasterBreakpoint(breakpoint)
    }
  }

  override suspend fun setMasterDependency(breakpointId: XBreakpointId, masterBreakpointId: XBreakpointId, isLeaveEnabled: Boolean) {
    val breakpoint = breakpointId.findValue() ?: return
    val masterBreakpoint = masterBreakpointId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      val dependentBreakpointManager = (XDebuggerManager.getInstance(breakpoint.project) as XDebuggerManagerImpl).breakpointManager.dependentBreakpointManager
      dependentBreakpointManager.setMasterBreakpoint(breakpoint, masterBreakpoint, isLeaveEnabled)
    }
  }

  private fun collectInitialDependencies(dependentBreakpointManager: XDependentBreakpointManager): List<XBreakpointDependencyDto> {
    return dependentBreakpointManager.allSlaveBreakpoints.mapNotNull { childBreakpoint ->
      return@mapNotNull createDto(dependentBreakpointManager, childBreakpoint)
    }
  }

  private fun createDto(
    dependentBreakpointManager: XDependentBreakpointManager,
    childBreakpoint: XBreakpoint<*>,
    parentBreakpoint: XBreakpoint<*>? = dependentBreakpointManager.getMasterBreakpoint(childBreakpoint),
  ): XBreakpointDependencyDto? {
    if (parentBreakpoint !is XBreakpointBase<*, *, *> || childBreakpoint !is XBreakpointBase<*, *, *>) {
      return null
    }
    val isLeaveEnabled = dependentBreakpointManager.isLeaveEnabled(childBreakpoint)
    return XBreakpointDependencyDto(
      child = childBreakpoint.breakpointId,
      parent = parentBreakpoint.breakpointId,
      isLeaveEnabled = isLeaveEnabled
    )
  }

  private fun createDependencyEventsFlow(project: Project, dependentBreakpointManager: XDependentBreakpointManager): Flow<XBreakpointDependencyEvent> =
    channelFlow {
      val channel = this
      project.messageBus.connect(this).subscribe(XDependentBreakpointListener.TOPIC, object : XDependentBreakpointListener {
        override fun dependencySet(slave: XBreakpoint<*>, master: XBreakpoint<*>) {
          val breakpointDependency = createDto(dependentBreakpointManager, slave, master) ?: return
          channel.trySend(XBreakpointDependencyEvent.Add(breakpointDependency))
        }

        override fun dependencyCleared(slave: XBreakpoint<*>) {
          val childBreakpointId = (slave as? XBreakpointBase<*, *, *>)?.breakpointId ?: return
          channel.trySend(XBreakpointDependencyEvent.Remove(childBreakpointId))
        }
      })

      awaitCancellation()
    }.buffer(Channel.BUFFERED)
}