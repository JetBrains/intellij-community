// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.impl.breakpoints.*
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy.Companion.useFeLineBreakpointProxy
import com.intellij.xdebugger.impl.rpc.XBreakpointApi
import com.intellij.xdebugger.impl.rpc.XBreakpointDto
import com.intellij.xdebugger.impl.rpc.XBreakpointId
import com.intellij.xdebugger.impl.rpc.XDebuggerManagerApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentMap

internal class FrontendXBreakpointManager(private val project: Project, private val cs: CoroutineScope) : XBreakpointManagerProxy {
  private val breakpointsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  private val breakpoints: ConcurrentMap<XBreakpointId, XBreakpointProxy> = ConcurrentCollectionFactory.createConcurrentMap()

  private var _breakpointsDialogSettings: XBreakpointsDialogState? = null

  private val lineBreakpointManager = XLineBreakpointManager(project, cs, isEnabled = useFeLineBreakpointProxy())

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
      val typesChangedFlow = FrontendXBreakpointTypesManager.getInstance(project).typesChangedFlow()
      XDebuggerManagerApi.getInstance().getBreakpoints(project.projectId()).combine(typesChangedFlow) { breakpointDtos, _ ->
        // this way we subscribe on breakpoint types updates
        breakpointDtos
      }.collectLatest { breakpointDtos ->
        val breakpointsToRemove = breakpoints.keys - breakpointDtos.map { it.id }.toSet()
        removeBreakpointsLocally(breakpointsToRemove)

        val newBreakpoints = breakpointDtos.filter { it.id !in breakpoints }
        for (breakpointDto in newBreakpoints) {
          addBreakpoint(breakpointDto)
        }
      }
    }
  }

  override fun addBreakpoint(breakpointDto: XBreakpointDto): XBreakpointProxy? {
    val currentBreakpoint = breakpoints[breakpointDto.id]
    if (currentBreakpoint != null) {
      return currentBreakpoint
    }
    val type = FrontendXBreakpointTypesManager.getInstance(project).getTypeById(breakpointDto.typeId)
    if (type == null) {
      return null
    }
    val newBreakpoint = createXBreakpointProxy(project, cs, breakpointDto, type, this, onBreakpointChange = {
      breakpointsChanged.tryEmit(Unit)
      if (it is XLineBreakpointProxy) {
        lineBreakpointManager.breakpointChanged(it)
      }
    })
    val previousBreakpoint = breakpoints.put(breakpointDto.id, newBreakpoint)
    if (newBreakpoint is XLineBreakpointProxy) {
      lineBreakpointManager.registerBreakpoint(newBreakpoint, true)
    }
    previousBreakpoint?.dispose()
    breakpointsChanged.tryEmit(Unit)
    return newBreakpoint
  }

  private fun removeBreakpointsLocally(breakpointsToRemove: Collection<XBreakpointId>) {
    for (breakpointToRemove in breakpointsToRemove) {
      val removedBreakpoint = breakpoints.remove(breakpointToRemove)
      removedBreakpoint?.dispose()
      if (removedBreakpoint is XLineBreakpointProxy) {
        lineBreakpointManager.unregisterBreakpoint(removedBreakpoint)
      }
    }
  }

  fun getBreakpointById(breakpointId: XBreakpointId): XBreakpointProxy? {
    return breakpoints[breakpointId]
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

  override fun getLineBreakpointManager(): XLineBreakpointManager {
    return lineBreakpointManager
  }

  override fun getAllBreakpointTypes(): List<XBreakpointTypeProxy> {
    return FrontendXBreakpointTypesManager.getInstance(project).getBreakpointTypes()
  }

  override fun getLineBreakpointTypes(): List<XLineBreakpointTypeProxy> {
    return FrontendXBreakpointTypesManager.getInstance(project).getLineBreakpointTypes()
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
    removeBreakpointsLocally(setOf(breakpoint.id))
    breakpointsChanged.tryEmit(Unit)
    cs.launch {
      XBreakpointApi.getInstance().removeBreakpoint(breakpoint.id)
    }
  }

  override fun removeBreakpoints(breakpoints: Collection<XBreakpointProxy>) {
    for (breakpoint in breakpoints) {
      removeBreakpoint(breakpoint)
    }
  }

  override fun findBreakpointsAtLine(type: XLineBreakpointTypeProxy, file: VirtualFile, line: Int): List<XLineBreakpointProxy> {
    return breakpoints.values.filterIsInstance<XLineBreakpointProxy>().filter {
      it.type == type && it.getFile()?.url == file.url && it.getLine() == line
    }
  }
}