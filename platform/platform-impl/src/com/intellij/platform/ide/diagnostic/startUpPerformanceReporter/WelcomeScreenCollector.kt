// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.diagnostic.startUpPerformanceReporter

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.createDurationField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.components.Service
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer.ProjectId
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.WelcomeScreenCollector.Event.FrameBecameInteractiveEvent
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.WelcomeScreenCollector.Event.FrameBecameVisibleEvent
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.WelcomeScreenCollector.Event.NonModalWelcomeScreenBecameVisibleEvent
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.WelcomeScreenCollector.Event.ProjectOpeningEvent
import com.intellij.util.containers.ComparatorUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Volatile
private var statsIsWritten = false

internal object WelcomeScreenCollector {
  private val channel = Channel<Event>(Int.MAX_VALUE)

  private sealed interface Event {
    val time: Long

    sealed class ProjectDependentEvent(
      val projectId: ProjectId,
      override val time: Long = System.nanoTime(),
    ) : Event

    data class ModalWelcomeScreenBecameVisibleEvent(override val time: Long = System.nanoTime()) : Event

    class FrameBecameVisibleEvent(
      projectId: ProjectId,
    ) : ProjectDependentEvent(projectId)

    class FrameBecameInteractiveEvent(
      projectId: ProjectId,
    ) : ProjectDependentEvent(projectId)

    class ProjectOpeningEvent(
      projectId: ProjectId,
      val isNonModalWelcomeScreenProject: Boolean,
    ) : ProjectDependentEvent(projectId)

    class NonModalWelcomeScreenBecameVisibleEvent(
      projectId: ProjectId,
    ) : ProjectDependentEvent(projectId)
  }

  fun shouldNotStart() {
    channel.close()
    statsIsWritten = true
  }

  fun modalWelcomeScreenBecameVisible() {
    channel.trySend(Event.ModalWelcomeScreenBecameVisibleEvent())
  }

  fun frameBecameVisible(projectId: ProjectId) {
    channel.trySend(FrameBecameVisibleEvent(projectId))
  }

  fun frameBecameInteractive(projectId: ProjectId) {
    channel.trySend(FrameBecameInteractiveEvent(projectId))
  }

  fun projectIsOpening(projectId: ProjectId, isNonModalWelcomeScreenProject: Boolean) {
    channel.trySend(ProjectOpeningEvent(projectId, isNonModalWelcomeScreenProject))
  }

  fun nonModalWelcomeScreenBecameVisible(projectId: ProjectId) {
    channel.trySend(NonModalWelcomeScreenBecameVisibleEvent(projectId))
  }

  suspend fun startWritingStatistics() {
    withContext(Dispatchers.IO) {
      try {
        //ensures non-thread-safe structures work correctly on different threads
        Mutex().withLock {
          doHandleStatisticEvents()
        }
      }
      finally {
        channel.close()
        statsIsWritten = true
      }
    }
  }

  // Is supposed to be invoked from [startWritingStatistics] only.
  // Runs under mutex lock to ensure safe usage of non-thread-safe maps
  private suspend fun doHandleStatisticEvents() {
    val frameBecameVisibleEventMap: MutableMap<ProjectId, FrameBecameVisibleEvent> = mutableMapOf()
    val frameBecameInteractiveEventMap: MutableMap<ProjectId, FrameBecameInteractiveEvent> = mutableMapOf()
    val projectOpeningEventMap: MutableMap<ProjectId, ProjectOpeningEvent> = mutableMapOf()
    val nonModalWelcomeScreenBecameVisibleEventMap: MutableMap<ProjectId, NonModalWelcomeScreenBecameVisibleEvent> = mutableMapOf()
    val fullyReportedProjects = mutableSetOf<ProjectId>()

    fun <V : Event.ProjectDependentEvent> MutableMap<ProjectId, V>.putIfAbsent(event: V) {
      putIfAbsent(event.projectId, event)
    }

    for (event in channel) {
      yield()
      @Suppress("IntroduceWhenSubject")
      when {
        event is Event.ModalWelcomeScreenBecameVisibleEvent -> {
          val duration = getDurationFromStart(event.time, null)
          WELCOME_SCREEN_BECAME_VISIBLE.log(duration, true, 0 /* no projects are available in this case */)
          return
        }
        event is Event.ProjectDependentEvent && fullyReportedProjects.contains(event.projectId) -> {
          continue
        }
        event is FrameBecameVisibleEvent -> {
          frameBecameVisibleEventMap.putIfAbsent(event)
        }
        event is FrameBecameInteractiveEvent -> {
          frameBecameInteractiveEventMap.putIfAbsent(event)
        }
        event is ProjectOpeningEvent -> {
          projectOpeningEventMap.putIfAbsent(event)
        }
        event is NonModalWelcomeScreenBecameVisibleEvent -> {
          nonModalWelcomeScreenBecameVisibleEventMap.putIfAbsent(event)
        }
      }

      fun projectHandlingFinished(projectId: ProjectId) {
        frameBecameVisibleEventMap.remove(projectId)
        frameBecameInteractiveEventMap.remove(projectId)
        fullyReportedProjects.add(projectId)
      }

      val projectIterator = projectOpeningEventMap.iterator()
      while (projectIterator.hasNext()) {
        val (projectId, event) = projectIterator.next()
        if (!event.isNonModalWelcomeScreenProject) {
          projectHandlingFinished(projectId)
          projectIterator.remove()
        }
      }

      val welcomeScreenIterator = nonModalWelcomeScreenBecameVisibleEventMap.iterator()
      while (welcomeScreenIterator.hasNext()) {
        val (projectId, event) = welcomeScreenIterator.next()
        val frameBecameVisibleEvent = frameBecameVisibleEventMap[projectId] ?: continue
        val frameBecameInteractiveEvent = frameBecameInteractiveEventMap[projectId] ?: continue
        reportEvents(projectId,
                     frameBecameVisibleEvent,
                     frameBecameInteractiveEvent,
                     event)
        projectHandlingFinished(projectId)
        projectOpeningEventMap.remove(projectId)
        welcomeScreenIterator.remove()
      }

      if (fullyReportedProjects.isNotEmpty() &&
          frameBecameVisibleEventMap.isEmpty() &&
          frameBecameInteractiveEventMap.isEmpty() &&
          projectOpeningEventMap.isEmpty() &&
          nonModalWelcomeScreenBecameVisibleEventMap.isEmpty()) {
        return
      }
    }
  }

  private fun getDurationFromStart(
    finishTimestampNano: Long = System.nanoTime(),
    lastReportedDuration: Duration?,
  ): Duration {
    val duration = (finishTimestampNano - StartUpMeasurer.getStartTime()).toDuration(DurationUnit.NANOSECONDS)
    return if (lastReportedDuration == null) duration else ComparatorUtil.max(duration, lastReportedDuration)
  }

  fun reportOldWelcomeScreenEvent(
    welcomeScreedDurationForFUS: Duration,
    splashScreenFUSDuration: Duration?,
  ) {
    if (splashScreenFUSDuration == null) {
      WELCOME_SCREEN_EVENT.log(DURATION.with(welcomeScreedDurationForFUS),
                               SPLASH_SCREEN_WAS_SHOWN.with(false))
    }
    else {
      WELCOME_SCREEN_EVENT.log(DURATION.with(welcomeScreedDurationForFUS),
                               SPLASH_SCREEN_WAS_SHOWN.with(true),
                               SPLASH_SCREEN_VISIBLE_DURATION.with(splashScreenFUSDuration))
    }
  }

  private fun reportEvents(
    projectId: ProjectId,
    frameBecameVisibleEvent: FrameBecameVisibleEvent,
    frameBecameInteractiveEvent: FrameBecameInteractiveEvent,
    nonModalWelcomeScreenBecameVisibleEvent: NonModalWelcomeScreenBecameVisibleEvent,
  ) {
    val frameVisibleDuration = getDurationFromStart(frameBecameVisibleEvent.time, null)
    FRAME_BECAME_VISIBLE_EVENT.log(frameVisibleDuration, projectId.projectOrder)

    val frameInteractiveDuration = getDurationFromStart(frameBecameInteractiveEvent.time, frameVisibleDuration)
    FRAME_BECAME_INTERACTIVE_EVENT.log(frameInteractiveDuration, projectId.projectOrder)

    val welcomeScreenDuration = getDurationFromStart(nonModalWelcomeScreenBecameVisibleEvent.time, frameInteractiveDuration)
    WELCOME_SCREEN_BECAME_VISIBLE.log(welcomeScreenDuration, false, projectId.projectOrder)
  }
}

private val GROUP = EventLogGroup("welcome.screen.startup.performance", 2)

private val SPLASH_SCREEN_WAS_SHOWN = EventFields.Boolean("splash_screen_was_shown")
private val SPLASH_SCREEN_VISIBLE_DURATION = createDurationField(DurationUnit.MILLISECONDS, "splash_screen_became_visible_duration_ms")
private val DURATION = createDurationField(DurationUnit.MILLISECONDS, "duration_ms")
private val WELCOME_SCREEN_EVENT = GROUP.registerVarargEvent(
  "welcome.screen.shown",
  DURATION, SPLASH_SCREEN_WAS_SHOWN, SPLASH_SCREEN_VISIBLE_DURATION,
)


private val IS_MODAL = EventFields.Boolean("is_modal")
private val PROJECT_ORDER_FIELD = EventFields.Int("project_order")
private val WELCOME_SCREEN_BECAME_VISIBLE = GROUP.registerEvent("welcome.screen.became.visible",
                                                                DURATION, IS_MODAL, PROJECT_ORDER_FIELD)

private val FRAME_BECAME_VISIBLE_EVENT = GROUP.registerEvent("non.modal.welcome.screen.frame.became.visible",
                                                             DURATION, PROJECT_ORDER_FIELD)

private val FRAME_BECAME_INTERACTIVE_EVENT = GROUP.registerEvent("non.modal.welcome.screen.frame.became.interactive",
                                                                 DURATION, PROJECT_ORDER_FIELD)

internal class WelcomeScreenPerformanceCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP
}

@TestOnly
@Service(Service.Level.APP)
class WelcomeScreenCollectorService {
  @TestOnly
  fun isHandlingFinished(): Boolean {
    return statsIsWritten
  }
}