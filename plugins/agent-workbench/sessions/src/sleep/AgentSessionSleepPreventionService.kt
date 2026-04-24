// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.sleep

// @spec community/plugins/agent-workbench/spec/agent-sessions-sleep-prevention.spec.md

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.service.AgentSessionReadService
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

internal const val SLEEP_PREVENTION_RELEASE_DEBOUNCE_MS: Long = 30_000

@Service(Service.Level.APP)
internal class AgentSessionSleepPreventionService(
  serviceScope: CoroutineScope,
  private val sessionsStateFlow: StateFlow<AgentSessionsState>,
  private val settingFlow: StateFlow<Boolean>,
  private val powerSaveModeFlow: StateFlow<Boolean>,
  private val sleepInhibitor: AgentSleepInhibitor,
  releaseSchedulerFactory: (CoroutineScope) -> AgentSleepReleaseScheduler,
  private val processingContext: AgentSleepPreventionExecutionContext,
  private val releaseDebounceMillis: Long,
  observeStateChanges: Boolean,
) : Disposable {
  @Suppress("unused")
  constructor(serviceScope: CoroutineScope) : this(
    serviceScope = serviceScope,
    sessionsStateFlow = service<AgentSessionReadService>().stateFlow(),
    settingFlow = createSleepPreventionSettingFlow(serviceScope),
    powerSaveModeFlow = createPowerSaveModeFlow(serviceScope),
    sleepInhibitor = createAgentSleepInhibitor(),
    releaseSchedulerFactory = ::CoroutineAgentSleepReleaseScheduler,
    processingContext = createAgentSleepPreventionExecutionContext(),
    releaseDebounceMillis = SLEEP_PREVENTION_RELEASE_DEBOUNCE_MS,
    observeStateChanges = true,
  )

  @Suppress("RAW_SCOPE_CREATION")
  private val processingScope = CoroutineScope(
    SupervisorJob(serviceScope.coroutineContext[Job]) +
    processingContext.dispatcher +
    CoroutineName("AgentSessionSleepPreventionService"),
  )
  private val releaseScheduler = releaseSchedulerFactory(processingScope)
  private var pendingRelease: AgentSleepReleaseHandle? = null
  private var blockerHeld = false
  private val disposalStarted = AtomicBoolean(false)
  @Volatile
  private var disposed = false
  private val controllerJob = processingScope.launch(start = CoroutineStart.UNDISPATCHED) {
    try {
      if (observeStateChanges) {
        launch {
          sessionsStateFlow.collect {
            applyCurrentState()
          }
        }
        launch {
          settingFlow.collect {
            applyCurrentState()
          }
        }
        launch {
          powerSaveModeFlow.collect {
            applyCurrentState()
          }
        }
      }
      awaitCancellation()
    }
    finally {
      disposed = true
      cancelPendingRelease()
      releaseHeldBlocker()
      Disposer.dispose(sleepInhibitor)
    }
  }

  @Suppress("RAW_RUN_BLOCKING")
  internal fun refreshState() {
    if (disposed) return
    runBlocking(Dispatchers.Default) {
      withContext(processingContext.dispatcher) {
        applyCurrentState()
      }
    }
  }

  @Suppress("RAW_RUN_BLOCKING")
  internal fun awaitProcessing() {
    if (disposed) return
    runBlocking(Dispatchers.Default) {
      withContext(processingContext.dispatcher) {
      }
    }
  }

  @Suppress("RAW_RUN_BLOCKING")
  override fun dispose() {
    if (!disposalStarted.compareAndSet(false, true)) {
      return
    }

    try {
      processingScope.cancel()
      runBlocking(Dispatchers.Default) {
        controllerJob.join()
      }
    }
    finally {
      processingContext.close()
    }
  }

  private fun applyCurrentState() {
    if (disposed) {
      return
    }

    if (!isSleepPreventionAllowed()) {
      cancelPendingRelease()
      releaseHeldBlocker()
      return
    }

    if (sessionsStateFlow.value.hasSleepPreventingWork()) {
      cancelPendingRelease()
      acquireBlocker()
    }
    else {
      scheduleRelease()
    }
  }

  private fun scheduleRelease() {
    if (!blockerHeld || pendingRelease != null) {
      return
    }

    pendingRelease = releaseScheduler.schedule(releaseDebounceMillis) {
      pendingRelease = null
      if (!disposed && (!isSleepPreventionAllowed() || !sessionsStateFlow.value.hasSleepPreventingWork())) {
        releaseHeldBlocker()
      }
    }
  }

  private fun isSleepPreventionAllowed(): Boolean {
    return settingFlow.value && !powerSaveModeFlow.value
  }

  private fun acquireBlocker() {
    if (blockerHeld) {
      return
    }

    blockerHeld = sleepInhibitor.acquire()
  }

  private fun releaseHeldBlocker() {
    if (!blockerHeld) {
      return
    }

    blockerHeld = false
    sleepInhibitor.release()
  }

  private fun cancelPendingRelease() {
    pendingRelease?.cancel()
    pendingRelease = null
  }
}

internal interface AgentSleepReleaseScheduler {
  fun schedule(delayMillis: Long, action: () -> Unit): AgentSleepReleaseHandle
}

internal fun interface AgentSleepReleaseHandle {
  fun cancel()
}

private class CoroutineAgentSleepReleaseScheduler(private val serviceScope: CoroutineScope) : AgentSleepReleaseScheduler {
  override fun schedule(delayMillis: Long, action: () -> Unit): AgentSleepReleaseHandle {
    val job = serviceScope.launch {
      delay(delayMillis.milliseconds)
      action()
    }
    return AgentSleepReleaseHandle {
      job.cancel()
    }
  }
}

internal interface AgentSleepPreventionExecutionContext : AutoCloseable {
  val dispatcher: CoroutineDispatcher
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun createAgentSleepPreventionExecutionContext(
  platform: AgentSleepPlatform = currentAgentSleepPlatform(),
): AgentSleepPreventionExecutionContext {
  return when (platform) {
    AgentSleepPlatform.WINDOWS -> DedicatedThreadAgentSleepPreventionExecutionContext()
    AgentSleepPlatform.MAC, AgentSleepPlatform.OTHER -> StaticAgentSleepPreventionExecutionContext(Dispatchers.Default.limitedParallelism(1))
  }
}

private class StaticAgentSleepPreventionExecutionContext(
  override val dispatcher: CoroutineDispatcher,
) : AgentSleepPreventionExecutionContext {
  override fun close() {
  }
}

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
private class DedicatedThreadAgentSleepPreventionExecutionContext : AgentSleepPreventionExecutionContext {
  private val closeableDispatcher = newSingleThreadContext("AgentWorkbenchSleepPrevention")

  override val dispatcher: CoroutineDispatcher = closeableDispatcher

  override fun close() {
    closeableDispatcher.close()
  }
}

private fun createSleepPreventionSettingFlow(serviceScope: CoroutineScope): StateFlow<Boolean> {
  val flow = MutableStateFlow(AgentSleepPreventionSettings.isEnabled())
  ApplicationManager.getApplication().messageBus.connect(serviceScope)
    .subscribe(AdvancedSettingsChangeListener.TOPIC, object : AdvancedSettingsChangeListener {
      override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
        if (id == PREVENT_SYSTEM_SLEEP_WHILE_WORKING_SETTING_ID) {
          flow.value = newValue as Boolean
        }
      }
    })
  return flow
}

private fun createPowerSaveModeFlow(serviceScope: CoroutineScope): StateFlow<Boolean> {
  val flow = MutableStateFlow(PowerSaveMode.isEnabled())
  ApplicationManager.getApplication().messageBus.connect(serviceScope)
    .subscribe(PowerSaveMode.TOPIC, PowerSaveMode.Listener {
      flow.value = PowerSaveMode.isEnabled()
    })
  return flow
}

private fun AgentSessionsState.hasSleepPreventingWork(): Boolean {
  return projects.any { project ->
    project.threads.any { thread -> thread.activity.preventsSystemSleep() } ||
    project.worktrees.any { worktree -> worktree.threads.any { thread -> thread.activity.preventsSystemSleep() } }
  }
}

private fun AgentThreadActivity.preventsSystemSleep(): Boolean {
  return this == AgentThreadActivity.PROCESSING || this == AgentThreadActivity.REVIEWING
}
