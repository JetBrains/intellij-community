// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.debugger.impl.rpc.XBreakpointApi
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.XLineBreakpointInstallationInfo
import com.intellij.xdebugger.impl.breakpoints.*
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy.Companion.useFeLineBreakpointProxy
import com.intellij.xdebugger.impl.rpc.*
import com.intellij.xdebugger.impl.toRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentMap

@ApiStatus.Internal
@VisibleForTesting
class FrontendXBreakpointManager(private val project: Project, private val cs: CoroutineScope) : XBreakpointManagerProxy {
  private val breakpointsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  private val breakpoints: ConcurrentMap<XBreakpointId, XBreakpointProxy> = ConcurrentCollectionFactory.createConcurrentMap()

  private var _breakpointsDialogSettings: XBreakpointsDialogState? = null

  private val lineBreakpointManager = XLineBreakpointManager(project, cs, isEnabled = useFeLineBreakpointProxy())

  private val lightBreakpoints: ConcurrentMap<LightBreakpointPosition, FrontendXLightLineBreakpoint> = ConcurrentCollectionFactory.createConcurrentMap()

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
      FrontendXBreakpointTypesManager.getInstance(project).typesInitialized().await()
      val (initialBreakpoints, breakpointEvents) = XDebuggerManagerApi.getInstance().getBreakpoints(project.projectId())
      for (breakpointDto in initialBreakpoints) {
        addBreakpoint(breakpointDto)
      }

      breakpointEvents.toFlow().collect { event ->
        when (event) {
          is XBreakpointEvent.BreakpointAdded -> {
            addBreakpoint(event.breakpointDto)
          }
          is XBreakpointEvent.BreakpointRemoved -> {
            removeBreakpointsLocally(setOf(event.breakpointId))
          }
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
    val previousBreakpoint = breakpoints.putIfAbsent(breakpointDto.id, newBreakpoint)
    if (previousBreakpoint != null) {
      newBreakpoint.dispose()
      return previousBreakpoint
    }
    if (newBreakpoint is XLineBreakpointProxy) {
      lineBreakpointManager.registerBreakpoint(newBreakpoint, true)
    }
    breakpointsChanged.tryEmit(Unit)
    return newBreakpoint
  }

  override fun canToggleLightBreakpoint(editor: Editor, info: XLineBreakpointInstallationInfo): Boolean {
    val type = info.types.singleOrNull() ?: return false
    if (findBreakpointsAtLine(type, info.position.file, info.position.line).isNotEmpty()) {
      return false
    }
    if (info.isTemporary || info.isConditional) {
      return false
    }
    val lineInfo = FrontendEditorLinesBreakpointsInfoManager.getInstance(project).getBreakpointsInfoForLineFast(editor, info.position.line)
    return lineInfo?.singleBreakpointVariant == true
  }

  override fun toggleLightBreakpoint(editor: Editor, installationInfo: XLineBreakpointInstallationInfo): Deferred<XLineBreakpointProxy?> {
    return cs.async {
      val lightBreakpointPosition = LightBreakpointPosition(installationInfo.position.file, installationInfo.position.line)
      val type = installationInfo.types.firstOrNull() ?: return@async null
      val lightBreakpoint = FrontendXLightLineBreakpoint(project, this@async, type, installationInfo, this@FrontendXBreakpointManager)
      try {
        val oldBreakpoint = lightBreakpoints.putIfAbsent(lightBreakpointPosition, lightBreakpoint)
        if (oldBreakpoint != null) {
          lightBreakpoint.dispose()
        }
        val response = XBreakpointTypeApi.getInstance().toggleLineBreakpoint(project.projectId(), installationInfo.toRequest(oldBreakpoint != null))

        withContext(Dispatchers.EDT + NonCancellable) {
          lightBreakpoints.remove(lightBreakpointPosition, lightBreakpoint)
          lightBreakpoint.dispose()
          when (response) {
            is XLineBreakpointInstalledResponse -> {
              val breakpointDto = response.breakpoint
              if (breakpointDto != null) {
                addBreakpoint(breakpointDto) as? XLineBreakpointProxy
              }
              else {
                null
              }
            }
            XRemoveBreakpointResponse -> {
              val breakpoint = XDebuggerUtilImpl.findBreakpointsAtLine(project, installationInfo).singleOrNull()
              if (breakpoint != null) {
                XDebuggerUtilImpl.removeBreakpointIfPossible(project, installationInfo, breakpoint)
              }
              null
            }
            else -> {
              null
            }
          }
        }
      }
      finally {
        lightBreakpoints.remove(lightBreakpointPosition, lightBreakpoint)
        lightBreakpoint.dispose()
      }
    }
  }

  private fun removeBreakpointsLocally(breakpointsToRemove: Collection<XBreakpointId>) {
    for (breakpointToRemove in breakpointsToRemove) {
      val removedBreakpoint = breakpoints.remove(breakpointToRemove)
      removedBreakpoint?.dispose()
      if (removedBreakpoint is XLineBreakpointProxy) {
        lineBreakpointManager.unregisterBreakpoint(removedBreakpoint)
      }
    }
    breakpointsChanged.tryEmit(Unit)
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

  @VisibleForTesting
  fun getBreakpointsSet(): Set<XBreakpointProxy> {
    return breakpoints.values.toSet()
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

  private data class LightBreakpointPosition(val file: VirtualFile, val line: Int)
}