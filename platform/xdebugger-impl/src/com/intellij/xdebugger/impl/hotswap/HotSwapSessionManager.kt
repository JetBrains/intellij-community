// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.containers.DisposableWrapperList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.SoftReference
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class HotSwapSessionManager private constructor(private val project: Project, internal val coroutineScope: CoroutineScope) {
  private val listeners = DisposableWrapperList<HotSwapChangesListener>()
  private val sessions = DisposableWrapperList<HotSwapSession<*>>()

  @Volatile
  private var selectedSession: SoftReference<HotSwapSession<*>>? = null

  /**
   * Start a hot swap session and source file tracking.
   *
   * @param provider platform-specific provider of hot swap
   * @param disposable handles the session end
   */
  fun <T> createSession(provider: HotSwapProvider<T>, disposable: Disposable): HotSwapSession<T> {
    val hotSwapSession = HotSwapSession(project, provider, coroutineScope)
    val removalDisposable = sessions.add(hotSwapSession, disposable)
    val sessionUnregisterDisposable = Disposable {
      val current = currentSession
      if (current !== hotSwapSession) return@Disposable
      selectedSession = null
      sessions.remove(hotSwapSession)
      val newCurrent = currentSession
      if (newCurrent != null) {
        fireStatusChanged(newCurrent)
      }
    }
    // Force the session to remain current (if it was) during unregistering
    Disposer.register(removalDisposable, sessionUnregisterDisposable)
    // Force disposing the session before removal from the list to be able to notify about the session end.
    Disposer.register(sessionUnregisterDisposable, hotSwapSession)
    hotSwapSession.init()
    return hotSwapSession
  }

  /**
   * Notify about session selection changes, e.g., switching between two debugger sessions.
   */
  fun onSessionSelected(session: HotSwapSession<*>) {
    if (session !in sessions) return
    val current = currentSession
    val selected = selectedSession?.get()
    if (selected !== session) {
      selectedSession = SoftReference(session)
    }
    if (session !== current) {
      fireStatusChanged(session)
    }
  }

  internal val currentSession: HotSwapSession<*>?
    get() {
      val selected = selectedSession?.get()
      if (selected != null) return selected
      return sessions.safeLastOrNull()
    }

  internal fun addListener(listener: HotSwapChangesListener, disposable: Disposable) {
    listeners.add(listener, disposable)
    if (currentSession == null) return
    listener.onStatusChanged(null)
  }

  internal fun fireStatusChanged(session: HotSwapSession<*>) {
    if (session !== currentSession) return
    listeners.forEach { it.onStatusChanged(null) }
  }

  internal fun notifyUpdate(forceStatus: HotSwapVisibleStatus? = null, session: HotSwapSession<*>? = null) {
    if (session != null && session !== currentSession) return
    listeners.forEach { it.onStatusChanged(forceStatus) }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): HotSwapSessionManager = project.service()
  }
}

internal enum class HotSwapVisibleStatus {
  // logical statuses used by HotSwapSession
  NO_CHANGES, CHANGES_READY, IN_PROGRESS, SESSION_COMPLETED,
  // additional visual statuses
  HIDDEN, SUCCESS
}

/**
 * Status change notification.
 * @see HotSwapSessionManager.currentSession
 * @see HotSwapSession.currentStatus
 */
internal fun interface HotSwapChangesListener {
  fun onStatusChanged(forceStatus: HotSwapVisibleStatus?)
}

@ApiStatus.Internal
class HotSwapSession<T> internal constructor(val project: Project, internal val provider: HotSwapProvider<T>, parentScope: CoroutineScope) : Disposable {
  internal val coroutineScope = parentScope.childScope("HotSwapSession $this")
  private lateinit var changesCollector: SourceFileChangesCollector<T>

  @Volatile
  internal var currentStatus: HotSwapVisibleStatus = HotSwapVisibleStatus.NO_CHANGES

  private fun setStatus(status: HotSwapVisibleStatus, fireUpdate: Boolean = true) {
    // No further updates after the session is complete
    if (currentStatus == HotSwapVisibleStatus.SESSION_COMPLETED) return
    currentStatus = status
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
  }

  /**
   * Get elements modified since the last hot swap.
   */
  fun getChanges() = changesCollector.getChanges()

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
          HotSwapSessionManager.getInstance(project).notifyUpdate(forceStatus, this@HotSwapSession)
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
