package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.sleep.AgentSessionSleepPreventionService
import com.intellij.agent.workbench.sessions.sleep.AgentSleepInhibitor
import com.intellij.agent.workbench.sessions.sleep.AgentSleepPreventionExecutionContext
import com.intellij.agent.workbench.sessions.sleep.AgentSleepReleaseHandle
import com.intellij.agent.workbench.sessions.sleep.AgentSleepReleaseScheduler
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentSessionSleepPreventionServiceTest {
  @Test
  fun acquiresImmediatelyWhenProcessingThreadAppears() {
    val fixture = sleepPreventionFixture()

    fixture.stateFlow.value = sessionsState(projectThreads = listOf(activeThread(AgentThreadActivity.PROCESSING)))
    fixture.service.refreshState()

    assertThat(fixture.inhibitor.acquireCalls).isEqualTo(1)
    assertThat(fixture.inhibitor.releaseCalls).isZero()
    fixture.dispose()
  }

  @Test
  fun reviewingThreadAlsoKeepsSystemAwake() {
    val fixture = sleepPreventionFixture()

    fixture.stateFlow.value = sessionsState(projectThreads = listOf(activeThread(AgentThreadActivity.REVIEWING)))
    fixture.service.refreshState()

    assertThat(fixture.inhibitor.acquireCalls).isEqualTo(1)
    fixture.dispose()
  }

  @Test
  fun releaseIsDebouncedUntilLastActiveThreadClears() {
    val fixture = sleepPreventionFixture()
    fixture.stateFlow.value = sessionsState(
      projectThreads = listOf(activeThread(AgentThreadActivity.PROCESSING)),
      worktreeThreads = listOf(activeThread(AgentThreadActivity.REVIEWING, id = "thread-2")),
    )
    fixture.service.refreshState()

    fixture.stateFlow.value = sessionsState(worktreeThreads = listOf(activeThread(AgentThreadActivity.REVIEWING, id = "thread-2")))
    fixture.service.refreshState()
    assertThat(fixture.scheduler.pendingCount()).isZero()

    fixture.stateFlow.value = sessionsState()
    fixture.service.refreshState()
    assertThat(fixture.scheduler.pendingCount()).isEqualTo(1)
    assertThat(fixture.inhibitor.releaseCalls).isZero()

    fixture.scheduler.runNext()

    assertThat(fixture.inhibitor.releaseCalls).isEqualTo(1)
    fixture.dispose()
  }

  @Test
  fun activeWorkCancelsPendingRelease() {
    val fixture = sleepPreventionFixture()
    fixture.stateFlow.value = sessionsState(projectThreads = listOf(activeThread(AgentThreadActivity.PROCESSING)))
    fixture.service.refreshState()

    fixture.stateFlow.value = sessionsState()
    fixture.service.refreshState()
    assertThat(fixture.scheduler.pendingCount()).isEqualTo(1)

    fixture.stateFlow.value = sessionsState(projectThreads = listOf(activeThread(AgentThreadActivity.PROCESSING)))
    fixture.service.refreshState()

    assertThat(fixture.scheduler.pendingCount()).isZero()
    fixture.scheduler.runCanceledActions()
    assertThat(fixture.inhibitor.releaseCalls).isZero()
    fixture.dispose()
  }

  @Test
  fun disablingSettingReleasesImmediatelyAndCancelsPendingDebounce() {
    val fixture = sleepPreventionFixture()
    fixture.stateFlow.value = sessionsState(projectThreads = listOf(activeThread(AgentThreadActivity.PROCESSING)))
    fixture.service.refreshState()

    fixture.stateFlow.value = sessionsState()
    fixture.service.refreshState()
    assertThat(fixture.scheduler.pendingCount()).isEqualTo(1)

    fixture.settingFlow.value = false
    fixture.service.refreshState()

    assertThat(fixture.inhibitor.releaseCalls).isEqualTo(1)
    assertThat(fixture.scheduler.pendingCount()).isZero()
    fixture.dispose()
  }

  @Test
  fun powerSaveModeBlocksAcquireWhileWorkIsActive() {
    val fixture = sleepPreventionFixture(powerSaveEnabled = true)

    fixture.stateFlow.value = sessionsState(projectThreads = listOf(activeThread(AgentThreadActivity.PROCESSING)))
    fixture.service.refreshState()

    assertThat(fixture.inhibitor.acquireCalls).isZero()
    assertThat(fixture.inhibitor.releaseCalls).isZero()
    fixture.dispose()
  }

  @Test
  fun enablingPowerSaveModeReleasesImmediately() {
    val fixture = sleepPreventionFixture()
    fixture.stateFlow.value = sessionsState(projectThreads = listOf(activeThread(AgentThreadActivity.PROCESSING)))
    fixture.service.refreshState()

    fixture.powerSaveModeFlow.value = true
    fixture.service.refreshState()

    assertThat(fixture.inhibitor.releaseCalls).isEqualTo(1)
    assertThat(fixture.scheduler.pendingCount()).isZero()
    fixture.dispose()
  }

  @Test
  fun disablingPowerSaveModeReacquiresWhileWorkIsStillActive() {
    val fixture = sleepPreventionFixture(powerSaveEnabled = true)
    fixture.stateFlow.value = sessionsState(projectThreads = listOf(activeThread(AgentThreadActivity.PROCESSING)))
    fixture.service.refreshState()
    assertThat(fixture.inhibitor.acquireCalls).isZero()

    fixture.powerSaveModeFlow.value = false
    fixture.service.refreshState()

    assertThat(fixture.inhibitor.acquireCalls).isEqualTo(1)
    fixture.dispose()
  }

  @Test
  fun enablingSettingWhilePowerSaveModeIsOnWaitsUntilPowerSaveModeTurnsOff() {
    val fixture = sleepPreventionFixture(enabled = false, powerSaveEnabled = true)
    fixture.stateFlow.value = sessionsState(projectThreads = listOf(activeThread(AgentThreadActivity.PROCESSING)))
    fixture.service.refreshState()

    fixture.settingFlow.value = true
    fixture.service.refreshState()
    assertThat(fixture.inhibitor.acquireCalls).isZero()

    fixture.powerSaveModeFlow.value = false
    fixture.service.refreshState()

    assertThat(fixture.inhibitor.acquireCalls).isEqualTo(1)
    fixture.dispose()
  }

  @Test
  fun powerSaveModeCancelsPendingReleaseAndReleasesImmediately() {
    val fixture = sleepPreventionFixture()
    fixture.stateFlow.value = sessionsState(projectThreads = listOf(activeThread(AgentThreadActivity.PROCESSING)))
    fixture.service.refreshState()

    fixture.stateFlow.value = sessionsState()
    fixture.service.refreshState()
    assertThat(fixture.scheduler.pendingCount()).isEqualTo(1)

    fixture.powerSaveModeFlow.value = true
    fixture.service.refreshState()

    assertThat(fixture.inhibitor.releaseCalls).isEqualTo(1)
    assertThat(fixture.scheduler.pendingCount()).isZero()
    fixture.dispose()
  }

  @Test
  fun enablingSettingWhileWorkIsAlreadyActiveAcquiresImmediately() {
    val fixture = sleepPreventionFixture(enabled = false)
    fixture.stateFlow.value = sessionsState(projectThreads = listOf(activeThread(AgentThreadActivity.PROCESSING)))
    fixture.service.refreshState()
    assertThat(fixture.inhibitor.acquireCalls).isZero()

    fixture.settingFlow.value = true
    fixture.service.refreshState()

    assertThat(fixture.inhibitor.acquireCalls).isEqualTo(1)
    fixture.dispose()
  }

  @Test
  fun observedStateChangesUpdateSleepPreventionWithoutManualRefresh() {
    val fixture = sleepPreventionFixture(observeStateChanges = true)

    fixture.stateFlow.value = sessionsState(projectThreads = listOf(activeThread(AgentThreadActivity.PROCESSING)))
    fixture.service.awaitProcessing()

    assertThat(fixture.inhibitor.acquireCalls).isEqualTo(1)

    fixture.stateFlow.value = sessionsState()
    fixture.service.awaitProcessing()

    assertThat(fixture.scheduler.pendingCount()).isEqualTo(1)

    fixture.scheduler.runNext()

    assertThat(fixture.inhibitor.releaseCalls).isEqualTo(1)
    fixture.dispose()
  }

  @Test
  fun observedPowerSaveModeChangesReleaseAndReacquireWithoutManualRefresh() {
    val fixture = sleepPreventionFixture(observeStateChanges = true)

    fixture.stateFlow.value = sessionsState(projectThreads = listOf(activeThread(AgentThreadActivity.PROCESSING)))
    fixture.service.awaitProcessing()
    assertThat(fixture.inhibitor.acquireCalls).isEqualTo(1)

    fixture.powerSaveModeFlow.value = true
    fixture.service.awaitProcessing()
    assertThat(fixture.inhibitor.releaseCalls).isEqualTo(1)

    fixture.powerSaveModeFlow.value = false
    fixture.service.awaitProcessing()
    assertThat(fixture.inhibitor.acquireCalls).isEqualTo(2)
    fixture.dispose()
  }

  @Test
  fun disposeReleasesImmediately() {
    val fixture = sleepPreventionFixture()
    fixture.stateFlow.value = sessionsState(projectThreads = listOf(activeThread(AgentThreadActivity.PROCESSING)))
    fixture.service.refreshState()

    Disposer.dispose(fixture.service)

    assertThat(fixture.inhibitor.releaseCalls).isEqualTo(1)
    fixture.scope.cancel()
  }
}

private data class SleepPreventionFixture(
  val scope: CoroutineScope,
  val stateFlow: MutableStateFlow<AgentSessionsState>,
  val settingFlow: MutableStateFlow<Boolean>,
  val powerSaveModeFlow: MutableStateFlow<Boolean>,
  val inhibitor: RecordingServiceSleepInhibitor,
  val scheduler: ManualSleepReleaseScheduler,
  val service: AgentSessionSleepPreventionService,
) {
  fun dispose() {
    Disposer.dispose(service)
    scope.cancel()
  }
}

private fun sleepPreventionFixture(
  enabled: Boolean = true,
  powerSaveEnabled: Boolean = false,
  observeStateChanges: Boolean = false,
): SleepPreventionFixture {
  @Suppress("RAW_SCOPE_CREATION")
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  val stateFlow = MutableStateFlow(AgentSessionsState())
  val settingFlow = MutableStateFlow(enabled)
  val powerSaveModeFlow = MutableStateFlow(powerSaveEnabled)
  val inhibitor = RecordingServiceSleepInhibitor()
  val scheduler = ManualSleepReleaseScheduler()
  val processingContext = TestAgentSleepPreventionExecutionContext()
  val service = AgentSessionSleepPreventionService(
    serviceScope = scope,
    sessionsStateFlow = stateFlow,
    settingFlow = settingFlow,
    powerSaveModeFlow = powerSaveModeFlow,
    sleepInhibitor = inhibitor,
    releaseSchedulerFactory = { scheduler },
    processingContext = processingContext,
    releaseDebounceMillis = 30_000,
    observeStateChanges = observeStateChanges,
  )
  return SleepPreventionFixture(scope, stateFlow, settingFlow, powerSaveModeFlow, inhibitor, scheduler, service)
}

private fun sessionsState(
  projectThreads: List<com.intellij.agent.workbench.sessions.core.AgentSessionThread> = emptyList(),
  worktreeThreads: List<com.intellij.agent.workbench.sessions.core.AgentSessionThread> = emptyList(),
): AgentSessionsState {
  return AgentSessionsState(
    projects = listOf(
      AgentProjectSessions(
        path = PROJECT_PATH,
        name = "Project A",
        isOpen = true,
        hasLoaded = true,
        threads = projectThreads,
        worktrees = listOf(
          AgentWorktree(
            path = WORKTREE_PATH,
            name = "feature/worktree",
            branch = "feature/worktree",
            isOpen = true,
            hasLoaded = true,
            threads = worktreeThreads,
          ),
        ),
      ),
    ),
  )
}

private fun activeThread(activity: AgentThreadActivity, id: String = "thread-1") = thread(
  id = id,
  updatedAt = 1,
  provider = com.intellij.agent.workbench.sessions.core.AgentSessionProvider.CODEX,
  activity = activity,
)

private class RecordingServiceSleepInhibitor : AgentSleepInhibitor {
  var acquireCalls: Int = 0
  var releaseCalls: Int = 0
  private var held = false

  override fun acquire(): Boolean {
    acquireCalls++
    held = true
    return true
  }

  override fun release() {
    if (!held) {
      return
    }

    held = false
    releaseCalls++
  }
}

private class ManualSleepReleaseScheduler : AgentSleepReleaseScheduler {
  private val scheduled = ArrayDeque<ManualScheduledRelease>()

  override fun schedule(delayMillis: Long, action: () -> Unit): AgentSleepReleaseHandle {
    val release = ManualScheduledRelease(action)
    scheduled.addLast(release)
    return AgentSleepReleaseHandle {
      release.canceled = true
    }
  }

  fun pendingCount(): Int {
    return scheduled.count { !it.canceled }
  }

  fun runNext() {
    val next = scheduled.firstOrNull { !it.canceled } ?: return
    scheduled.remove(next)
    next.action()
  }

  fun runCanceledActions() {
    val canceled = scheduled.filter { it.canceled }.toList()
    scheduled.removeAll(canceled)
    canceled.forEach { it.action() }
  }
}

private data class ManualScheduledRelease(
  val action: () -> Unit,
  var canceled: Boolean = false,
)

private class TestAgentSleepPreventionExecutionContext : AgentSleepPreventionExecutionContext {
  override val dispatcher = Dispatchers.Unconfined

  override fun close() {
  }
}
