// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.impl.breakpoints.*
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem
import com.intellij.xdebugger.impl.rpc.XBreakpointApi
import com.intellij.xdebugger.impl.rpc.XBreakpointId
import com.intellij.xdebugger.impl.rpc.XDebuggerManagerApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentMap

private val LOG = logger<FrontendXBreakpointManager>()

internal class FrontendXBreakpointManager(private val project: Project, private val cs: CoroutineScope) : XBreakpointManagerProxy {
  private val breakpointsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  private val breakpoints: ConcurrentMap<XBreakpointId, FrontendXBreakpointProxy> = ConcurrentCollectionFactory.createConcurrentMap()

  private var _breakpointsDialogSettings: XBreakpointsDialogState? = null

  // TODO[IJPL-160384]: support persistance between sessions
  override val breakpointsDialogSettings: XBreakpointsDialogState?
    get() = _breakpointsDialogSettings

  override val allGroups: Set<String>
    get() = setOf() // TODO: implement groups


  override val dependentBreakpointManager: XDependentBreakpointManagerProxy =
    FrontendXDependentBreakpointManagerProxy(project, cs, breakpointById = {
      breakpoints[it]
    })

  init {
    cs.launch {
      val breakpointTypesManager = FrontendXBreakpointTypesManager.getInstanceSuspending(project)
      XDebuggerManagerApi.getInstance().getBreakpoints(project.projectId()).collectLatest { breakpointDtos ->
        val breakpointsToRemove = breakpoints.keys - breakpointDtos.map { it.id }.toSet()
        removeBreakpoints(breakpointsToRemove)

        val newBreakpoints = breakpointDtos.filter { it.id !in breakpoints }
        for (breakpointDto in newBreakpoints) {
          val type = breakpointTypesManager.getTypeById(breakpointDto.typeId)
          if (type == null) {
            LOG.error("Breakpoint type with id ${breakpointDto.typeId} not found")
            continue
          }
          breakpoints[breakpointDto.id] = FrontendXBreakpointProxy(project, cs, breakpointDto, type, onBreakpointChange = {
            breakpointsChanged.tryEmit(Unit)
          })
        }
      }
    }
  }

  private fun removeBreakpoints(breakpointsToRemove: Collection<XBreakpointId>) {
    for (breakpointToRemove in breakpointsToRemove) {
      val removedBreakpoint = breakpoints.remove(breakpointToRemove)
      removedBreakpoint?.dispose()
    }
  }

  override fun setBreakpointsDialogSettings(settings: XBreakpointsDialogState) {
    _breakpointsDialogSettings = settings
  }

  override fun setDefaultGroup(group: String) {
    // TODO: implement groups
  }

  override fun getAllBreakpointItems(): List<BreakpointItem> {
    return breakpoints.values.map { proxy ->
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

  override fun getLastRemovedBreakpoint(): XBreakpointProxy? {
    // TODO: Send through RPC
    return null
  }

  override fun removeBreakpoint(breakpoint: XBreakpointProxy) {
    removeBreakpoints(setOf(breakpoint.id))
    cs.launch {
      XBreakpointApi.getInstance().removeBreakpoint(breakpoint.id)
    }
  }
}