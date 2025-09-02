// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.asDisposable
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.hotswap.HotSwapInDebugSessionEnabler
import com.intellij.xdebugger.hotswap.HotSwapSession
import com.intellij.xdebugger.hotswap.HotSwapSessionManager
import com.intellij.xdebugger.impl.XDebugSessionImpl
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap


@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class HotSwapDebugSessionManager(project: Project, cs: CoroutineScope) : XDebuggerManagerListener {
  private val sessions = ConcurrentHashMap<XDebugProcess, HotSwapSessionEntry>()

  init {
    project.getMessageBus().connect(cs).subscribe(XDebuggerManager.TOPIC, this)
  }

  fun findHotSwapSession(process: XDebugProcess): HotSwapSession<*>? = sessions[process]?.hotSwapSession

  override fun processStarted(debugProcess: XDebugProcess) {
    if (!Registry.`is`("debugger.hotswap.floating.toolbar")) return
    val provider = HotSwapInDebugSessionEnabler.createProviderForProcess(debugProcess) ?: return
    val xDebugSession = debugProcess.session as XDebugSessionImpl
    val disposable = xDebugSession.coroutineScope.asDisposable()
    val hotSwapSession = HotSwapSessionManager.getInstance(xDebugSession.project).createSession(provider, disposable)
    sessions[debugProcess] = HotSwapSessionEntry(hotSwapSession, disposable)
  }

  override fun processStopped(debugProcess: XDebugProcess) {
    val sessionEntry = sessions.remove(debugProcess) ?: return
    Disposer.dispose(sessionEntry.disposable)
  }

  override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
    if (currentSession == null) return
    val (_, entry) = sessions.entries.firstOrNull { (process, _) -> process.session === currentSession } ?: return
    HotSwapSessionManager.getInstance(currentSession.project).onSessionSelected(entry.hotSwapSession)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): HotSwapDebugSessionManager = project.service()
  }
}

private data class HotSwapSessionEntry(val hotSwapSession: HotSwapSession<*>, val disposable: Disposable)

private class HotSwapManagerInitActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.serviceAsync<HotSwapDebugSessionManager>()
  }
}