// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.hotswap.*
import com.intellij.xdebugger.impl.rpc.HotSwapVisibleStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
class HotSwapSessionManagerImpl private constructor(private val project: Project, private val coroutineScope: CoroutineScope) : HotSwapSessionManager {
  private val sessions = CopyOnWriteArrayList<HotSwapSessionImpl<*>>()

  @Volatile
  private var selectedSession: WeakReference<HotSwapSessionImpl<*>>? = null
  private val _currentStatusFlow = MutableStateFlow(null as? CurrentSessionState?)

  val currentStatusFlow: StateFlow<CurrentSessionState?> = _currentStatusFlow

  override fun <T> createSession(provider: HotSwapProvider<T>, disposable: Disposable): HotSwapSession<T> {
    val hotSwapSession = HotSwapSessionImpl(project, provider, coroutineScope)
    Disposer.register(disposable, hotSwapSession)
    sessions.add(hotSwapSession)
    hotSwapSession.init()
    fireStatusChanged(hotSwapSession)
    return hotSwapSession
  }

  internal fun onSessionDispose(session: HotSwapSessionImpl<*>) {
    val currentStatus = _currentStatusFlow.value
    if (session === currentSession) {
      selectedSession = null
    }
    sessions.remove(session)
    if (currentStatus?.session === session) {
      // Do not leak session via status
      _currentStatusFlow.compareAndSet(currentStatus, null)
    }
    val newCurrent = currentSession
    if (newCurrent != null) {
      fireStatusChanged(newCurrent)
    }
  }

  /**
   * Notify about session selection changes, e.g., switching between two debugger sessions.
   */
  override fun onSessionSelected(session: HotSwapSession<*>) {
    if (session !is HotSwapSessionImpl<*>) return
    if (session !in sessions) return
    val selected = selectedSession?.get()
    if (selected !== session) {
      selectedSession = WeakReference(session)
    }
    fireStatusChanged(session)
  }

  /**
   * Resets status to [HotSwapVisibleStatus.HIDDEN], so that the next update will be reported to listeners.
   */
  fun hide() {
    val currentState = _currentStatusFlow.value ?: return
    _currentStatusFlow.compareAndSet(currentState, currentState.copy(status = HotSwapVisibleStatus.HIDDEN))
  }

  internal val currentSession: HotSwapSessionImpl<*>?
    get() {
      val selected = selectedSession?.get()
      if (selected != null) return selected
      return sessions.safeLastOrNull()
    }

  internal fun fireStatusChanged(session: HotSwapSession<*>, forceStatus: HotSwapVisibleStatus? = null) {
    if (session !== currentSession) return
    val status = forceStatus ?: session.currentStatus
    _currentStatusFlow.value = CurrentSessionState(session, status)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): HotSwapSessionManagerImpl = HotSwapSessionManager.getInstance(project) as HotSwapSessionManagerImpl

    internal fun getInstanceOrNull(project: Project): HotSwapSessionManagerImpl? = project.serviceIfCreated<HotSwapSessionManager>() as? HotSwapSessionManagerImpl
  }
}

@ApiStatus.Internal
data class CurrentSessionState(val session: HotSwapSessionImpl<*>, val status: HotSwapVisibleStatus)

private val logger = logger<HotSwapSession<*>>()

private val COMPLETED_STATUS: HotSwapVisibleStatus? = null

@ApiStatus.Internal
class HotSwapSessionImpl<T> internal constructor(
  override val project: Project,
  private val provider: HotSwapProvider<T>,
  parentScope: CoroutineScope,
) : HotSwapSession<T>, Disposable {

  private val coroutineScope = parentScope.childScope("HotSwapSession $this")
  private lateinit var changesCollector: SourceFileChangesCollector<T>

  internal val currentStatus: HotSwapVisibleStatus get() = _currentStatus.get() ?: HotSwapVisibleStatus.NO_CHANGES
  private val _currentStatus = AtomicReference(HotSwapVisibleStatus.NO_CHANGES as HotSwapVisibleStatus?)

  private fun setStatus(status: HotSwapVisibleStatus?, fireUpdate: Boolean = true) {
    while (true) {
      val curStatus = _currentStatus.get()
      // No further updates after the session is complete
      if (curStatus == COMPLETED_STATUS) return
      if (_currentStatus.compareAndSet(curStatus, status)) break
    }
    if (logger.isDebugEnabled) {
      logger.debug("Session status changed: $status (fire=$fireUpdate)")
    }
    if (fireUpdate) {
      HotSwapSessionManagerImpl.getInstance(project).fireStatusChanged(this)
    }
  }

  internal fun init() {
    changesCollector = provider.createChangesCollector(this, coroutineScope, SessionSourceFileChangesListener())
    Disposer.register(this, changesCollector)
  }

  override fun dispose() {
    HotSwapStatusNotificationManager.getInstanceOrNull(project)?.clearNotifications()
    // Avoid triggering HotSwapSessionManager.getInstance() from dispose()
    setStatus(COMPLETED_STATUS, fireUpdate = false)
    coroutineScope.cancel()
    HotSwapSessionManagerImpl.getInstanceOrNull(project)?.onSessionDispose(this)
  }

  fun performHotSwap(): Unit = provider.performHotSwap(this)

  override fun getChanges(): Set<T> = changesCollector.getChanges()

  override fun startHotSwapListening(): HotSwapResultListener {
    HotSwapStatusNotificationManager.getInstance(project).clearNotifications()
    val statusBefore = currentStatus
    setStatus(HotSwapVisibleStatus.IN_PROGRESS)
    val completed = AtomicBoolean()
    return object : HotSwapResultListener {
      override fun onSuccessfulReload() {
        completeHotSwap(true, HotSwapVisibleStatus.NO_CHANGES, HotSwapVisibleStatus.SUCCESS)
      }

      override fun onFinish() {
        completeHotSwap(true, HotSwapVisibleStatus.NO_CHANGES)
      }

      override fun onFailure() {
        completeHotSwap(resetChanges = false, HotSwapVisibleStatus.NO_CHANGES)
      }

      override fun onCanceled() {
        completeHotSwap(false, statusBefore)
      }

      private fun completeHotSwap(resetChanges: Boolean, status: HotSwapVisibleStatus, forceStatus: HotSwapVisibleStatus? = null) {
        if (!completed.compareAndSet(false, true)) return
        if (resetChanges) {
          changesCollector.resetChanges()
        }
        val customFire = forceStatus != null
        setStatus(status, fireUpdate = !customFire)
        if (customFire) {
          HotSwapSessionManagerImpl.getInstance(project).fireStatusChanged(this@HotSwapSessionImpl, forceStatus)
        }
        if (forceStatus == HotSwapVisibleStatus.SUCCESS) {
          HotSwapStatusNotificationManager.getInstance(project).showSuccessNotification(coroutineScope)
        }
      }
    }
  }

  private inner class SessionSourceFileChangesListener : SourceFileChangesListener {
    override fun onNewChanges() {
      setStatus(HotSwapVisibleStatus.CHANGES_READY)
    }

    override fun onChangesCanceled() {
      setStatus(HotSwapVisibleStatus.NO_CHANGES)
    }
  }
}

/**
 * Thread safe implementation of `lastOrNull` call.
 * List itself should be thread-safe.
 */
private fun <T> List<T>.safeLastOrNull(): T? {
  while (true) {
    val size = size
    if (size == 0) return null
    try {
      return this[size - 1]
    }
    catch (_: IndexOutOfBoundsException) {
      continue
    }
  }
}
