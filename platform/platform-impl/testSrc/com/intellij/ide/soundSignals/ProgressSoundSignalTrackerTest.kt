// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicatorModel
import com.intellij.openapi.progress.ProgressModel
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.progress.impl.taskInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The test keeps one real clock, because the container owns the player scope. Virtual time is the fallback if this
 * ever turns flaky.
 */
@TestApplication
@RegistryKey(key = SOUND_SIGNALS_ENABLED_REGISTRY_KEY, value = "true")
@RegistryKey(key = INITIAL_DELAY_REGISTRY_KEY, value = "300")
@RegistryKey(key = REPEAT_DELAY_REGISTRY_KEY, value = "150")
@Timeout(120)
class ProgressSoundSignalTrackerTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `nothing plays before the initial delay elapses`() = trackerTest { tracker, player ->
    tracker.track(TestProgress())

    delay(IDLE_WAIT)

    assertThat(player.recorded).isEmpty()
  }

  @Test
  fun `a signal plays once the initial delay elapses`() = trackerTest { tracker, player ->
    tracker.track(TestProgress())

    assertThat(player.awaitPlay()).containsExactly(INDETERMINATE)
  }

  @Test
  fun `the signal repeats while the progress stays`() = trackerTest { tracker, player ->
    tracker.track(TestProgress())

    repeat(3) { assertThat(player.awaitPlay()).containsExactly(INDETERMINATE) }
  }

  @Test
  fun `a progress that ends before the initial delay is silent`() = trackerTest { tracker, player ->
    val progress = TestProgress()
    tracker.track(progress)
    progress.finish()

    delay(INITIAL_DELAY + REPEAT_DELAY * 3)

    assertThat(player.recorded).isEmpty()
  }

  @Test
  fun `a progress that finished before it was tracked is silent`() = trackerTest { tracker, player ->
    val progress = TestProgress()
    progress.finish()
    tracker.track(progress)

    delay(INITIAL_DELAY + REPEAT_DELAY * 3)

    assertThat(player.recorded).isEmpty()
  }

  @Test
  fun `the last progress finish stops the signal`() = trackerTest { tracker, player ->
    val progress = TestProgress()
    tracker.track(progress)
    player.awaitPlay()

    progress.finish()

    player.assertStopped()
  }

  @Test
  fun `a tick drops a finished progress whose finish action never ran`() = trackerTest { tracker, player ->
    val progress = TestProgress(dropsFinishActions = true)
    tracker.track(progress)
    player.awaitPlay()

    progress.finish()

    player.assertStopped()
  }

  @Test
  fun `a closed project stops the signal of its progress`() = trackerTest { tracker, player ->
    val project = mockProject()
    tracker.track(TestProgress(project))
    player.awaitPlay()

    Disposer.dispose(project)

    player.assertStopped()
  }

  @Test
  fun `a second progress does not restart the initial delay`() = trackerTest { tracker, player ->
    val start = TimeSource.Monotonic.markNow()
    tracker.track(TestProgress())
    delay(INITIAL_DELAY / 2)
    tracker.track(TestProgress())

    assertThat(player.awaitPlay()).containsExactly(INDETERMINATE)
    // a restart would push the first signal past one and a half initial delays
    assertThat(start.elapsedNow()).isLessThan(INITIAL_DELAY * 1.5)
  }

  @Test
  fun `two status bars share one signal stream`() = trackerTest { tracker, player ->
    tracker.track(TestProgress(mockProject()))
    tracker.track(TestProgress(mockProject()))

    // one batch per tick, and one signal per batch
    repeat(3) { assertThat(player.awaitPlay()).containsExactly(INDETERMINATE) }
  }

  @Test
  fun `the signal stops only once every progress finishes`() = trackerTest { tracker, player ->
    val first = TestProgress()
    tracker.track(first)
    tracker.track(TestProgress())
    player.awaitPlay()

    first.finish()

    assertThat(player.awaitPlay()).containsExactly(INDETERMINATE)
  }

  @Test
  fun `a new progress after a silence waits the initial delay again`() = trackerTest { tracker, player ->
    val first = TestProgress()
    tracker.track(first)
    player.awaitPlay()
    first.finish()
    delay(REPEAT_DELAY * 3)

    val beforeRestart = player.recorded.size
    tracker.track(TestProgress())
    delay(IDLE_WAIT)

    assertThat(player.recorded.size).isEqualTo(beforeRestart)
    player.awaitPlay()
  }

  @Test
  fun `a rapid handoff waits the initial delay again`() = trackerTest { tracker, player ->
    val first = TestProgress()
    tracker.track(first)
    player.awaitPlay()

    val handoffStartedAt = System.nanoTime()
    first.finish()
    tracker.track(TestProgress())

    val (signals, playedAt) = player.awaitTimedPlay()
    assertThat(signals).containsExactly(INDETERMINATE)
    assertThat((playedAt - handoffStartedAt).nanoseconds).isGreaterThanOrEqualTo(INITIAL_DELAY - 30.milliseconds)
  }

  @Test
  fun `each tick plays the sound of the current progress`() = trackerTest { tracker, player ->
    val progress = TestProgress()
    tracker.track(progress)
    assertThat(player.awaitPlay()).containsExactly(INDETERMINATE)

    progress.setFraction(0.1)
    player.awaitPlayOf(STAGE_1)

    progress.setFraction(0.9)
    player.awaitPlayOf(STAGE_3)
  }

  @Test
  fun `with no active window the first progress decides`() = trackerTest { tracker, player ->
    tracker.track(TestProgress(mockProject(), fraction = 0.1))
    tracker.track(TestProgress(mockProject(), fraction = 0.5))

    repeat(2) { assertThat(player.awaitPlay()).containsExactly(STAGE_1) }
  }

  @Test
  fun `the fraction picks the stage by thirds`() {
    assertThat(progressSoundSignal(indeterminate = true, fraction = 0.5)).isSameAs(INDETERMINATE)
    assertThat(progressSoundSignal(indeterminate = true, fraction = 1.0)).isSameAs(INDETERMINATE)
    assertThat(progressSoundSignal(indeterminate = false, fraction = 0.0)).isSameAs(STAGE_1)
    assertThat(progressSoundSignal(indeterminate = false, fraction = 0.33)).isSameAs(STAGE_1)
    assertThat(progressSoundSignal(indeterminate = false, fraction = 1.0 / 3)).isSameAs(STAGE_2)
    assertThat(progressSoundSignal(indeterminate = false, fraction = 0.5)).isSameAs(STAGE_2)
    assertThat(progressSoundSignal(indeterminate = false, fraction = 2.0 / 3)).isSameAs(STAGE_3)
    assertThat(progressSoundSignal(indeterminate = false, fraction = 1.0)).isSameAs(STAGE_3)
  }

  @Test
  fun `a muted Progress signal plays nothing`() = trackerTest(muteProgress = true) { tracker, player ->
    tracker.track(TestProgress())

    delay(INITIAL_DELAY + REPEAT_DELAY * 3)

    assertThat(player.recorded).isEmpty()
    assertThat(tracker.isTimerRunning).isFalse()
  }

  @Test
  fun `a mute stops the timer at the next tick`() = trackerTest { tracker, player ->
    tracker.track(TestProgress())
    player.awaitPlay()

    for (signal in PROGRESS_SIGNALS) service<AccessibilitySettings>().setSignal(signal, false)
    delay(REPEAT_DELAY * 2)

    assertThat(tracker.isTimerRunning).isFalse()
    player.assertStopped()
  }

  @Test
  @RegistryKey(key = SOUND_SIGNALS_ENABLED_REGISTRY_KEY, value = "false")
  fun `no tracker while the feature is off`() = trackerTest { _, _ ->
    assertThat(ProgressSoundSignalTracker.getInstanceIfEnabled()).isNull()
  }

  @Test
  fun `no tracker while every Progress signal is off`() = trackerTest(muteProgress = true) { _, _ ->
    assertThat(ProgressSoundSignalTracker.getInstanceIfEnabled()).isNull()
  }

  /** The tracker is a light service, so this is the only check that the container can build it. */
  @Test
  fun `the tracker resolves as an application service`() = trackerTest { _, _ ->
    assertThat(service<ProgressSoundSignalTracker>()).isNotNull()
    assertThat(ProgressSoundSignalTracker.getInstanceIfEnabled()).isNotNull()
  }

  private fun trackerTest(
    muteProgress: Boolean = false,
    body: suspend (ProgressSoundSignalTracker, RecordingPlayer) -> Unit,
  ): Unit = timeoutRunBlocking(60.seconds) {
    val player = RecordingPlayer()
    withSoundSignalsSettings { settings ->
      val trackerScope = childScope("ProgressSoundSignalTracker under test")
      try {
        for (signal in PROGRESS_SIGNALS) settings.setSignal(signal, !muteProgress)
        ApplicationManager.getApplication().replaceService(SoundSignalPlayer::class.java, player, disposable)

        body(ProgressSoundSignalTracker(trackerScope), player)
      }
      finally {
        trackerScope.cancel()
      }
    }
  }

  private fun mockProject(): Project = MockProject(null, disposable)

  private fun ProgressSoundSignalTracker.track(progress: TestProgress) {
    track(progress.project, progress.model, progress.info)
  }

  private class RecordingPlayer : SoundSignalPlayer() {
    private val all = CopyOnWriteArrayList<List<SoundSignal>>()
    private val batches = Channel<Pair<List<SoundSignal>, Long>>(Channel.UNLIMITED)

    override fun playEnabled(signals: Collection<SoundSignal>) {
      val batch = signals.toList()
      all += batch
      batches.trySend(batch to System.nanoTime())
    }

    val recorded: List<List<SoundSignal>> get() = all.toList()

    suspend fun awaitPlay(): List<SoundSignal> = awaitTimedPlay().first

    suspend fun awaitTimedPlay(): Pair<List<SoundSignal>, Long> = batches.receive()

    /** Skips the ticks that ran before a progress change. */
    suspend fun awaitPlayOf(signal: SoundSignal) {
      do {
        val batch = awaitPlay()
      }
      while (signal !in batch)
    }

    suspend fun assertStopped() {
      val afterStop = recorded.size
      delay(REPEAT_DELAY * 5)
      // at most one more signal, from a tick that had already started or that drops the progress
      assertThat(recorded.size - afterStop).isLessThanOrEqualTo(1)
    }
  }

  private class TestProgress(
    val project: Project? = null,
    fraction: Double? = null,
    dropsFinishActions: Boolean = false,
  ) {
    private val indicatorModel = ProgressIndicatorModel(TITLE, TaskCancellation.nonCancellable()) {}

    val model: ProgressModel =
      if (dropsFinishActions) object : ProgressModel by indicatorModel {
        override fun addOnFinishAction(action: (TaskInfo) -> Unit) {}
      }
      else indicatorModel

    val info: TaskInfo = taskInfo(TITLE, TaskCancellation.nonCancellable())

    init {
      indicatorModel.setIndeterminate(true)
      fraction?.let(::setFraction)
    }

    fun setFraction(fraction: Double) {
      indicatorModel.setIndeterminate(false)
      indicatorModel.setFraction(fraction)
    }

    fun finish() {
      indicatorModel.finish(info)
    }
  }

  private companion object {
    /** The registry values on the class. */
    val INITIAL_DELAY = 300.milliseconds
    val REPEAT_DELAY = 150.milliseconds

    /** Well inside [INITIAL_DELAY], so a slow machine cannot turn a silence check flaky. */
    val IDLE_WAIT = 60.milliseconds

    const val TITLE = "Test progress"

    val INDETERMINATE = IdeSoundSignals.PROGRESS_INDETERMINATE
    val STAGE_1 = IdeSoundSignals.PROGRESS_DETERMINATE_STAGE_1
    val STAGE_2 = IdeSoundSignals.PROGRESS_DETERMINATE_STAGE_2
    val STAGE_3 = IdeSoundSignals.PROGRESS_DETERMINATE_STAGE_3
    val PROGRESS_SIGNALS = listOf(INDETERMINATE, STAGE_1, STAGE_2, STAGE_3)
  }
}
