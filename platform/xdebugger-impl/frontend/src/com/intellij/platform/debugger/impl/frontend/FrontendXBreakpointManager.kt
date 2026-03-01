// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.rpc.XBreakpointApi
import com.intellij.platform.debugger.impl.rpc.XBreakpointDto
import com.intellij.platform.debugger.impl.rpc.XBreakpointEvent
import com.intellij.platform.debugger.impl.rpc.XBreakpointId
import com.intellij.platform.debugger.impl.rpc.XBreakpointTypeApi
import com.intellij.platform.debugger.impl.rpc.XDebuggerManagerApi
import com.intellij.platform.debugger.impl.shared.BreakpointRequestCounter
import com.intellij.platform.debugger.impl.shared.InlineBreakpointsCache
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointTypeProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDependentBreakpointManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointInstallationInfo
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointTypeProxy
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.impl.breakpoints.XBreakpointItem
import com.intellij.xdebugger.impl.breakpoints.XBreakpointsDialogState
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointManager
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem
import fleet.rpc.client.RpcClientException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.time.Duration.Companion.seconds

private val log = logger<FrontendXBreakpointManager>()

@ApiStatus.Internal
@VisibleForTesting
class FrontendXBreakpointManager(private val project: Project, private val cs: CoroutineScope) : XBreakpointManagerProxy {
  private val breakpointsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val breakpointsChangedWithReplay = breakpointsChanged.shareIn(cs, SharingStarted.Eagerly, replay = 1)

  private val breakpoints: ConcurrentMap<XBreakpointId, XBreakpointProxy> = ConcurrentCollectionFactory.createConcurrentMap()

  private var _breakpointsDialogSettings: XBreakpointsDialogState? = null

  private val lineBreakpointManager = XLineBreakpointManager(project, cs, isEnabled = SplitDebuggerMode.isSplitDebugger(), this)

  private val lightBreakpoints: ConcurrentMap<LightBreakpointPosition, FrontendXLightLineBreakpoint> = ConcurrentCollectionFactory.createConcurrentMap()

  private var lastRemovedBreakpoint: XBreakpointProxy? = null

  private var defaultGroup: String? = null

  // TODO[IJPL-160384]: support persistance between sessions
  override val breakpointsDialogSettings: XBreakpointsDialogState?
    get() = _breakpointsDialogSettings


  override val dependentBreakpointManager: XDependentBreakpointManagerProxy =
    FrontendXDependentBreakpointManagerProxy(project, cs, breakpointById = {
      breakpoints[it]
    })

  override val inlineBreakpointsCache: InlineBreakpointsCache = FrontendInlineBreakpointsCache(project, cs, this)

  @VisibleForTesting
  val breakpointIdsRemovedLocally: MutableSet<XBreakpointId> = ConcurrentHashMap.newKeySet()

  internal val breakpointRequestCounter = BreakpointRequestCounter()

  init {
    cs.launch {
      FrontendXBreakpointTypesManager.getInstance(project).typesInitialized().await()
      initializeBreakpoints()
      durableWithStateReset(block = {
        defaultGroup = XBreakpointApi.getInstance().getDefaultGroup(project.projectId())
      }, stateReset = {
        defaultGroup = null
      })
    }
  }

  private fun initializeBreakpoints() = cs.launch {
    durableWithStateReset(block = {
      val (initialBreakpoints, breakpointEvents) = XDebuggerManagerApi.getInstance().getBreakpoints(project.projectId())
      for (breakpointDto in initialBreakpoints) {
        try {
          addBreakpoint(breakpointDto, updateUI = false)
        }
        catch (e: Throwable) {
          if (e is CancellationException || e is RpcClientException) throw e
          log.error("Error during initial breakpoints creation from backend $breakpointDto", e)
        }
      }

      lineBreakpointManager.queueAllBreakpointsUpdate()

      breakpointEvents.toFlow().collect { event ->
        try {
          when (event) {
            is XBreakpointEvent.BreakpointAdded -> {
              log.debug { "Breakpoint add request from backend: ${event.breakpointDto.id}" }
              addBreakpoint(event.breakpointDto, updateUI = true)
            }
            is XBreakpointEvent.BreakpointRemoved -> {
              log.debug { "Breakpoint removal request from backend: ${event.breakpointId}" }
              removeBreakpointLocally(event.breakpointId)
              // breakpointRemoved event happened on the server, so we can remove id from the frontend
              breakpointIdsRemovedLocally.remove(event.breakpointId)
            }
            is XBreakpointEvent.BreakpointPresentationUpdated -> {
              log.debug { "Breakpoint presentation update from backend: ${event.breakpointId}" }
              val breakpoint = breakpoints[event.breakpointId] as? FrontendXBreakpointProxy
              if (breakpoint != null) {
                breakpoint.updatePresentation(event.customPresentation, event.currentSessionCustomPresentation)
              } else {
                log.warn("Presentation update for unknown breakpoint: ${event.breakpointId}")
              }
            }
          }
        }
        catch (e: Throwable) {
          if (e is CancellationException || e is RpcClientException) throw e
          log.error("Error during breakpoint event processing from backend: $event", e)
        }
      }
    }, stateReset = {
      for (breakpoint in breakpoints.values) {
        removeBreakpointLocally(breakpoint.id)
      }
      breakpointIdsRemovedLocally.clear()
      breakpoints.clear()
    })
  }

  /**
   * Waits for breakpoint creation from [XBreakpointEvent.BreakpointAdded] event from backend.
   * Returns `null` in case of timeout.
   *
   * [addBreakpoint] is not called in parallel, to have only one source of truth and avoid races.
   */
  override suspend fun awaitBreakpointCreation(breakpointId: XBreakpointId): XBreakpointProxy? {
    return findOrAwaitElement(breakpointsChangedWithReplay, logMessage = breakpointId.toString()) {
      val currentBreakpoint = breakpoints[breakpointId]
      if (currentBreakpoint != null) {
        Ref.create(currentBreakpoint)
      }
      else if (breakpointId in breakpointIdsRemovedLocally) {
        Ref.create(null)
      }
      else {
        null
      }
    }
  }

  private fun addBreakpoint(breakpointDto: XBreakpointDto, updateUI: Boolean): XBreakpointProxy? {
    val currentBreakpoint = breakpoints[breakpointDto.id]
    if (currentBreakpoint != null) {
      log.debug { "Breakpoint creation skipped for ${breakpointDto.id}, because it already exists" }
      return currentBreakpoint
    }
    if (breakpointDto.id in breakpointIdsRemovedLocally) {
      // don't add breakpoints if it was already removed locally
      log.debug { "Breakpoint creation skipped for ${breakpointDto.id}, because it was removed locally" }
      return null
    }
    val type = FrontendXBreakpointTypesManager.getInstance(project).getTypeById(breakpointDto.typeId) ?: return null
    val newBreakpoint = createXBreakpointProxy(project, cs, breakpointDto, type, this)
    newBreakpoint.installListener {
      breakpointsChanged.tryEmit(Unit)
      if (newBreakpoint is XLineBreakpointProxy) {
        lineBreakpointManager.breakpointChanged(newBreakpoint)
      }
      if (newBreakpoint is FrontendXLineBreakpointProxy) {
        newBreakpoint.attachments.forEach { it.breakpointChanged() }
      }
    }
    val previousBreakpoint = breakpoints.putIfAbsent(breakpointDto.id, newBreakpoint)
    if (previousBreakpoint != null) {
      newBreakpoint.dispose()
      log.debug { "Breakpoint creation skipped for ${breakpointDto.id}, because it is already created" }
      return previousBreakpoint
    }
    (newBreakpoint as? FrontendXLineBreakpointProxy)?.registerInManager(updateUI)
    log.debug { "Breakpoint created for ${breakpointDto.id}" }
    breakpointsChanged.tryEmit(Unit)
    return newBreakpoint
  }

  private suspend fun canToggleLightBreakpoint(editor: Editor, info: XLineBreakpointInstallationInfo): Boolean {
    val type = info.types.singleOrNull() ?: return false
    if (findBreakpointsAtLine(type, info.position.file, info.position.line).isNotEmpty()) {
      return false
    }
    if (info.isTemporary || info.isLogging) {
      return false
    }
    val lineInfo = FrontendEditorLinesBreakpointsInfoManager.getInstance(project).getBreakpointsInfoForLine(editor, info.position.line)
    return lineInfo.singleBreakpointVariant
  }

  /**
   * Detects whether a breakpoint is likely to be installed with no need for user interaction,
   * and if so, shows a temporary (a.k.a. light) breakpoint during [block] execution.
   */
  override suspend fun <T> withLightBreakpointIfPossible(
    editor: Editor?, info: XLineBreakpointInstallationInfo, block: suspend () -> T,
  ): T {
    if (editor == null || !canToggleLightBreakpoint(editor, info)) {
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
    breakpointIdsRemovedLocally.add(breakpointId)
    val removedBreakpoint = breakpoints.remove(breakpointId)

    // Attachments are automatically disposed when the breakpoint's coroutine scope is cancelled via dispose()
    removedBreakpoint?.dispose()
    if (removedBreakpoint == null) {
      log.debug { "Breakpoint removal has no effect for $breakpointId, because it doesn't exist locally" }
    }
    else {
      log.debug { "Breakpoint removed for $breakpointId" }
    }
    (removedBreakpoint as? FrontendXLineBreakpointProxy)?.unregisterInManager()
    breakpointsChanged.tryEmit(Unit)
  }

  fun getBreakpointById(breakpointId: XBreakpointId): XBreakpointProxy? {
    return breakpoints[breakpointId]
  }

  override fun setBreakpointsDialogSettings(settings: XBreakpointsDialogState) {
    _breakpointsDialogSettings = settings
  }

  override fun getDefaultGroup(): String? {
    return defaultGroup
  }

  override fun setDefaultGroup(group: String?) {
    if (group == defaultGroup) return

    defaultGroup = group
    cs.launch {
      XBreakpointApi.getInstance().setDefaultGroup(project.projectId(), group)
    }
  }

  override fun getAllBreakpointItems(): List<BreakpointItem> {
    return breakpoints.values.map { proxy ->
      XBreakpointItem(proxy, this)
    }
  }

  override fun getAllBreakpoints(): List<XBreakpointProxy> {
    return breakpoints.values.toList()
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
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      breakpointsChanged.collect {
        withContext(Dispatchers.EDT) {
          listener()
        }
      }
    }
  }

  @VisibleForTesting
  fun getBreakpointsSet(): Set<XBreakpointProxy> {
    return breakpoints.values.toSet()
  }

  override fun getLastRemovedBreakpoint(): XBreakpointProxy? {
    return lastRemovedBreakpoint
  }

  override fun removeBreakpoint(breakpoint: XBreakpointProxy): CompletableFuture<Void?> {
    if (breakpoint.isDefaultBreakpoint()) {
      // removing default breakpoint should just disable it
      breakpoint.setEnabled(false)
      return CompletableFuture.completedFuture(null)
    }
    log.debug { "Breakpoint removal request from frontend: ${breakpoint.id}" }
    removeBreakpointLocally(breakpoint.id)
    breakpointsChanged.tryEmit(Unit)
    return cs.future {
      XBreakpointTypeApi.getInstance().removeBreakpoint(breakpoint.id)
      null
    }
  }

  override fun rememberRemovedBreakpoint(breakpoint: XBreakpointProxy) {
    lastRemovedBreakpoint = breakpoint
    cs.launch {
      XBreakpointTypeApi.getInstance().rememberRemovedBreakpoint(breakpoint.id)
    }
  }

  override fun restoreRemovedBreakpoint(breakpoint: XBreakpointProxy) {
    lastRemovedBreakpoint = null
    cs.launch {
      XBreakpointTypeApi.getInstance().restoreRemovedBreakpoint(breakpoint.project.projectId())
    }
  }

  override fun copyLineBreakpoint(breakpoint: XLineBreakpointProxy, file: VirtualFile, line: Int) {
    cs.launch {
      XBreakpointTypeApi.getInstance().copyLineBreakpoint(breakpoint.id, file.rpcId(), line)
    }
  }

  override fun onBreakpointRemoval(breakpoint: XLineBreakpointProxy, session: XDebugSessionProxy) {
    cs.launch {
      XBreakpointTypeApi.getInstance().onBreakpointRemoval(breakpoint.id, session.id)
    }
  }

  override fun findBreakpointsAtLine(type: XLineBreakpointTypeProxy, file: VirtualFile, line: Int): List<XLineBreakpointProxy> {
    return breakpoints.values.filterIsInstance<XLineBreakpointProxy>().filter {
      it.type == type && it.getFile()?.url == file.url && it.getLine() == line
    }
  }


  private data class LightBreakpointPosition(val file: VirtualFile, val line: Int)

  private fun FrontendXLineBreakpointProxy.registerInManager(updateUI: Boolean) {
    while (true) {
      val status = registrationInLineManagerStatus.get()
      when (status) {
        RegistrationStatus.DEREGISTERED -> return
        RegistrationStatus.IN_PROGRESS, RegistrationStatus.REGISTERED -> error("Breakpoint $id is already registered")
        RegistrationStatus.NOT_STARTED -> {
          if (!registrationInLineManagerStatus.compareAndSet(RegistrationStatus.NOT_STARTED, RegistrationStatus.IN_PROGRESS)) continue
          lineBreakpointManager.registerBreakpoint(this, updateUI)
          if (!registrationInLineManagerStatus.compareAndSet(RegistrationStatus.IN_PROGRESS, RegistrationStatus.REGISTERED)) {
            val newStatus = registrationInLineManagerStatus.get()
            check(newStatus == RegistrationStatus.DEREGISTERED) { "Unexpected status: $newStatus" }
            unregisterInManager()
          }
          return
        }
      }
    }
  }

  private fun FrontendXLineBreakpointProxy.unregisterInManager() {
    registrationInLineManagerStatus.set(RegistrationStatus.DEREGISTERED)
    lineBreakpointManager.unregisterBreakpoint(this)
  }
}

/**
 * Searches an element with [search] or suspends until the element appears.
 * Uses [updateFlow] as a trigger for updates.
 * [updateFlow] must be a flow with replay.
 * Returns `null` in case of timeout.
 */
internal suspend fun <T> findOrAwaitElement(
  updateFlow: Flow<*>,
  logMessage: String? = null,
  timeoutS: Int = 60, search: () -> Ref<T>?,
): T? {
  val existing = search()
  if (existing != null) return existing.get()

  if (logMessage != null) {
    log.debug { "[findOrAwaitElement] Waiting for creation event for $logMessage" }
  }
  return coroutineScope {
    val result = CompletableDeferred<T>()
    val job = launch {
      updateFlow.collect {
        if (logMessage != null) {
          log.debug { "[findOrAwaitElement] Flow updated, check for $logMessage" }
        }
        val existing = search()
        if (existing != null) {
          result.complete(existing.get())
        }
      }
    }
    try {
      return@coroutineScope withTimeout(timeoutS.seconds) {
        result.await()
      }
    }
    catch (_: TimeoutCancellationException) {
      log.error("[findOrAwaitElement] Failed to await for event for $logMessage in $timeoutS seconds")
      return@coroutineScope null
    }
    finally {
      job.cancel()
    }
  }
}
