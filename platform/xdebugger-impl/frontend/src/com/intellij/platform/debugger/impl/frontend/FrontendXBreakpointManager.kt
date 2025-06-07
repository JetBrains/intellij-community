// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.impl.XLineBreakpointInstallationInfo
import com.intellij.xdebugger.impl.breakpoints.*
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy.Companion.useFeLineBreakpointProxy
import com.intellij.xdebugger.impl.rpc.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

private val log = logger<FrontendXBreakpointManager>()

@ApiStatus.Internal
@VisibleForTesting
class FrontendXBreakpointManager(private val project: Project, private val cs: CoroutineScope) : XBreakpointManagerProxy {
  private val breakpointsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

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

  @VisibleForTesting
  val breakpointIdsRemovedLocally: MutableSet<XBreakpointId> = ConcurrentHashMap.newKeySet()

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
            log.info("Breakpoint removal request from backend: ${event.breakpointId}")
            removeBreakpointLocally(event.breakpointId)
            // breakpointRemoved event happened on the server, so we can remove id from the frontend
            breakpointIdsRemovedLocally.remove(event.breakpointId)
          }
        }
      }
    }
  }

  override fun addBreakpoint(breakpointDto: XBreakpointDto): XBreakpointProxy? {
    if (breakpointDto.id in breakpointIdsRemovedLocally) {
      // don't add breakpoints if it was already removed locally
      log.info("Breakpoint creation skipped for ${breakpointDto.id}, because it was removed locally")
      return null
    }
    val currentBreakpoint = breakpoints[breakpointDto.id]
    if (currentBreakpoint != null) {
      log.info("Breakpoint creation skipped for ${breakpointDto.id}, because it already exists")
      return currentBreakpoint
    }
    val type = FrontendXBreakpointTypesManager.getInstance(project).getTypeById(breakpointDto.typeId) ?: return null
    val newBreakpoint = createXBreakpointProxy(project, cs, breakpointDto, type, this, onBreakpointChange = {
      breakpointsChanged.tryEmit(Unit)
      if (it is XLineBreakpointProxy) {
        lineBreakpointManager.breakpointChanged(it)
      }
    })
    val previousBreakpoint = breakpoints.putIfAbsent(breakpointDto.id, newBreakpoint)
    if (previousBreakpoint != null) {
      newBreakpoint.dispose()
      log.info("Breakpoint creation skipped for ${breakpointDto.id}, because it is already created")
      return previousBreakpoint
    }
    if (newBreakpoint is XLineBreakpointProxy) {
      lineBreakpointManager.registerBreakpoint(newBreakpoint, true)
    }
    log.info("Breakpoint created for ${breakpointDto.id}")
    breakpointsChanged.tryEmit(Unit)
    return newBreakpoint
  }

  private fun canToggleLightBreakpoint(editor: Editor, info: XLineBreakpointInstallationInfo): Boolean {
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

  /**
   * Detects whether a breakpoint is likely to be installed with no need for user interaction,
   * and if so, shows a temporary (a.k.a. light) breakpoint during [block] execution.
   */
  override suspend fun <T> withLightBreakpointIfPossible(
    editor: Editor?, info: XLineBreakpointInstallationInfo, block: suspend () -> T,
  ): T {
    if (editor == null || !readAction { canToggleLightBreakpoint(editor, info) }) {
      return block()
    }
    val lightBreakpointPosition = LightBreakpointPosition(info.position.file, info.position.line)
    val type = info.types.first()
    return coroutineScope {
      val lightBreakpoint = createLightBreakpointIfPossible(lightBreakpointPosition, type, info, editor)
      try {
        block()
      }
      finally {
        lightBreakpoints.remove(lightBreakpointPosition, lightBreakpoint)
        lightBreakpoint?.dispose()
      }
    }
  }

  private suspend fun CoroutineScope.createLightBreakpointIfPossible(
    lightBreakpointPosition: LightBreakpointPosition,
    type: XLineBreakpointTypeProxy,
    info: XLineBreakpointInstallationInfo,
    editor: Editor,
  ): FrontendXLightLineBreakpoint? {
    while (true) {
      val newBreakpoint = FrontendXLightLineBreakpoint(project, this, type, info, this@FrontendXBreakpointManager)
      val oldBreakpoint: FrontendXLightLineBreakpoint? = lightBreakpoints.putIfAbsent(lightBreakpointPosition, newBreakpoint)
      if (oldBreakpoint == null) {
        return newBreakpoint
      }

      // there is a parallel request with a light breakpoint
      newBreakpoint.dispose()
      // wait for the previous request to complete
      oldBreakpoint.awaitDispose()

      // recheck the ability to install a light breakpoint
      if (!canToggleLightBreakpoint(editor, info)) return null
    }
  }

  private fun removeBreakpointLocally(breakpointId: XBreakpointId) {
    val removedBreakpoint = breakpoints.remove(breakpointId)
    removedBreakpoint?.dispose()
    if (removedBreakpoint == null) {
      log.info("Breakpoint removal has no effect for $breakpointId, because it doesn't exist locally")
    }
    else {
      log.info("Breakpoint removed for $breakpointId")
    }
    if (removedBreakpoint is XLineBreakpointProxy) {
      lineBreakpointManager.unregisterBreakpoint(removedBreakpoint)
    }
    breakpointIdsRemovedLocally.add(breakpointId)
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
    log.info("Breakpoint removal request from frontend: ${breakpoint.id}")
    removeBreakpointLocally(breakpoint.id)
    breakpointsChanged.tryEmit(Unit)
    cs.launch {
      XBreakpointTypeApi.getInstance().removeBreakpoint(breakpoint.id)
    }
  }

  override fun findBreakpointsAtLine(type: XLineBreakpointTypeProxy, file: VirtualFile, line: Int): List<XLineBreakpointProxy> {
    return breakpoints.values.filterIsInstance<XLineBreakpointProxy>().filter {
      it.type == type && it.getFile()?.url == file.url && it.getLine() == line
    }
  }

  private data class LightBreakpointPosition(val file: VirtualFile, val line: Int)
}
