// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
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
@Service(Service.Level.PROJECT)
class HotSwapSessionManager private constructor(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val sessions = CopyOnWriteArrayList<HotSwapSession<*>>()

  @Volatile
  private var selectedSession: WeakReference<HotSwapSession<*>>? = null
  private val _currentStatusFlow = MutableStateFlow(null as? CurrentSessionState?)

  val currentStatusFlow: StateFlow<CurrentSessionState?> = _currentStatusFlow

  /**
   * Start a hot swap session and source file tracking.
   *
   * @param provider platform-specific provider of hot swap
   * @param disposable handles the session end
   */
  fun <T> createSession(provider: HotSwapProvider<T>, disposable: Disposable): HotSwapSession<T> {
    val hotSwapSession = HotSwapSession(project, provider, coroutineScope)
    Disposer.register(disposable, hotSwapSession)
    sessions.add(hotSwapSession)
    hotSwapSession.init()
    fireStatusChanged(hotSwapSession)
    return hotSwapSession
  }

  internal fun onSessionDispose(session: HotSwapSession<*>) {
    if (session === currentSession) {
      selectedSession = null
    }
    sessions.remove(session)
    val newCurrent = currentSession
    if (newCurrent != null) {
      fireStatusChanged(newCurrent)
    }
  }

  /**
   * Notify about session selection changes, e.g., switching between two debugger sessions.
   */
  fun onSessionSelected(session: HotSwapSession<*>) {
    if (session !in sessions) return
    val current = currentSession
    val selected = selectedSession?.get()
    if (selected !== session) {
      selectedSession = WeakReference(session)
    }
    if (session !== current) {
      fireStatusChanged(session)
    }
  }

  /**
   * Resets status to [HotSwapVisibleStatus.HIDDEN], so that the next update will be reported to listeners.
   */
  fun hide() {
    val currentState = _currentStatusFlow.value ?: return
    _currentStatusFlow.compareAndSet(currentState, currentState.copy(status = HotSwapVisibleStatus.HIDDEN))
  }

  internal val currentSession: HotSwapSession<*>?
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
    fun getInstance(project: Project): HotSwapSessionManager = project.service()
  }
}

@ApiStatus.Internal
data class CurrentSessionState(val session: HotSwapSession<*>, val status: HotSwapVisibleStatus)

@ApiStatus.Internal
enum class HotSwapVisibleStatus {
  NO_CHANGES, CHANGES_READY, IN_PROGRESS, SESSION_COMPLETED, SUCCESS, HIDDEN
}

private val logger = logger<HotSwapSession<*>>()

@ApiStatus.Internal
class HotSwapSession<T> internal constructor(val project: Project, val provider: HotSwapProvider<T>, parentScope: CoroutineScope) : Disposable {
  private val coroutineScope = parentScope.childScope("HotSwapSession $this")
  private lateinit var changesCollector: SourceFileChangesCollector<T>

  internal val currentStatus: HotSwapVisibleStatus get() = _currentStatus.get()
  private val _currentStatus = AtomicReference(HotSwapVisibleStatus.NO_CHANGES)

  private fun setStatus(status: HotSwapVisibleStatus, fireUpdate: Boolean = true) {
    while (true) {
      val curStatus = currentStatus
      // No further updates after the session is complete
      if (curStatus == HotSwapVisibleStatus.SESSION_COMPLETED) return
      if (_currentStatus.compareAndSet(curStatus, status)) break
    }
    if (logger.isDebugEnabled) {
      logger.debug("Session status changed: $status (fire=$fireUpdate)")
    }
    if (fireUpdate) {
      HotSwapSessionManager.getInstance(project).fireStatusChanged(this)
    }
  }

  internal fun init() {
    changesCollector = provider.createChangesCollector(this, coroutineScope, SessionSourceFileChangesListener())
    Disposer.register(this, changesCollector)
  }

  override fun dispose() {
    HotSwapStatusNotificationManager.getInstance(project).clearNotifications()
    setStatus(HotSwapVisibleStatus.SESSION_COMPLETED)
    coroutineScope.cancel()
    HotSwapSessionManager.getInstance(project).onSessionDispose(this)
  }

  /**
   * Get elements modified since the last hot swap.
   */
  fun getChanges(): Set<T> = changesCollector.getChanges()

  /**
   * Start a hot swap process.
   * @return a callback to report the hot swap status
   */
  fun startHotSwapListening(): HotSwapResultListener {
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
          HotSwapSessionManager.getInstance(project).fireStatusChanged(this@HotSwapSession, forceStatus)
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
