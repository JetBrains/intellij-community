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
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class HotSwapSessionManager(private val project: Project, private val parentScope: CoroutineScope) {
  private val listeners = DisposableWrapperList<HotSwapChangesListener>()

  fun <T> createSession(provider: HotSwapProvider<T>, disposable: Disposable) {
    val hotSwapSession = HotSwapSession(project, provider, parentScope)
    Disposer.register(disposable, hotSwapSession)
    hotSwapSession.init()
  }

  internal fun addListener(listener: HotSwapChangesListener, disposable: Disposable) {
    listeners.add(listener, disposable)
  }

  internal fun fireStatusChanged(session: HotSwapSession<*>, status: HotSwapVisibleStatus) {
    listeners.forEach { it.onStatusChanged(session, status) }
  }

  companion object {
    fun getInstance(project: Project): HotSwapSessionManager = project.service()
  }
}

internal enum class HotSwapVisibleStatus {
  NO_CHANGES, CHANGES_READY, IN_PROGRESS, SESSION_COMPLETED
}

internal fun interface HotSwapChangesListener {
  fun onStatusChanged(session: HotSwapSession<*>, status: HotSwapVisibleStatus)
}

@ApiStatus.Internal
class HotSwapSession<T>(val project: Project, internal val provider: HotSwapProvider<T>, parentScope: CoroutineScope) : Disposable {
  internal val coroutineScope = parentScope.childScope("HotSwapSession $this")
  private val hasActiveChanges = AtomicBoolean()
  private lateinit var changesCollector: SourceFileChangesCollector<T>

  internal fun init() {
    changesCollector = provider.createChangesCollector(this, coroutineScope, SourceFileChangesListener {
      if (hasActiveChanges.compareAndSet(false, true)) {
        HotSwapSessionManager.getInstance(project).fireStatusChanged(this@HotSwapSession, HotSwapVisibleStatus.CHANGES_READY)
      }
    })
    Disposer.register(this, changesCollector)
  }

  override fun dispose() {
    HotSwapSessionManager.getInstance(project).fireStatusChanged(this, HotSwapVisibleStatus.SESSION_COMPLETED)
    coroutineScope.cancel()
  }

  private fun completeHotSwap() {
    if (hasActiveChanges.compareAndSet(true, false)) {
      changesCollector.resetChanges()
      HotSwapSessionManager.getInstance(project).fireStatusChanged(this, HotSwapVisibleStatus.NO_CHANGES)
    }
  }

  internal fun startHotSwap() {
    HotSwapSessionManager.getInstance(project).fireStatusChanged(this, HotSwapVisibleStatus.IN_PROGRESS)
  }

  fun createStatusListener() = object : HotSwapResultListener {
    override fun onCompleted() {
      completeHotSwap()
    }

    override fun onFailed() {
      completeHotSwap()
    }

    override fun onCanceled() {
    }
  }
}
