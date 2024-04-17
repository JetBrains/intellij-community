// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.diagnostic.startUpPerformanceReporter

import com.intellij.concurrency.currentThreadContext
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.idea.IdeStarter
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.events.EventFields.createDurationField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.containers.ComparatorUtil
import com.intellij.util.containers.ContainerUtil
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration


@Volatile
private var statsIsWritten = false

@Internal
object FUSProjectHotStartUpMeasurer {
  private val channel = Channel<Event>(Int.MAX_VALUE)

  enum class ProjectsType {
    Reopened,
    FromFilesToLoad, //see com.intellij.idea.IdeStarter.filesToLoad
    FromArgs,
    Unknown,
  }

  enum class Violation {
    MightBeLightEditProject,
    MultipleProjects,
    NoProjectFound,
    WelcomeScreenShown,
    OpeningURI,               //see com.intellij.idea.IdeStarter.uriToOpen
    ApplicationStarter,
    HasOpenedProject,
  }

  private suspend fun isProperContext(): Boolean {
    return currentCoroutineContext().isProperContext()
  }

  private fun CoroutineContext.isProperContext(): Boolean {
    return this[MyMarker] != null
  }

  private sealed interface Event {
    /**
     * It's an event corresponding to a FUS event, which is not a terminal one.
     * See their list at https://youtrack.jetbrains.com/issue/IJPL-269
     */
    sealed interface FUSReportableEvent : Event

    class SplashBecameVisibleEvent : FUSReportableEvent {
      val time: Long = System.nanoTime()
    }

    class WelcomeScreenEvent : Event {
      val time: Long = System.nanoTime()
    }

    class ViolationEvent(val violation: Violation) : Event {
      val time: Long = System.nanoTime()
    }

    class ProjectTypeReportEvent(val projectsType: ProjectsType) : Event
    class ProjectPathReportEvent(val hasSettings: Boolean) : Event
    class FrameBecameVisibleEvent : FUSReportableEvent {
      val time: Long = System.nanoTime()
    }

    class FrameBecameInteractiveEvent : FUSReportableEvent {
      val time: Long = System.nanoTime()
    }

    class MarkupRestoredEvent(val fileId: Int) : Event
    class FirstEditorEvent(
      val sourceOfSelectedEditor: SourceOfSelectedEditor,
      val file: VirtualFile,
      val time: Long
    ) : Event

    class NoMoreEditorsEvent(val time: Long) : Event

    data object ResetProjectPathEvent : Event
    data object IdeStarterStartedEvent : Event
  }

  /**
   * Might happen before [getStartUpContextElementIntoIdeStarter]
   */
  fun splashBecameVisible() {
    channel.trySend(Event.SplashBecameVisibleEvent())
  }

  fun getStartUpContextElementIntoIdeStarter(ideStarter: IdeStarter): CoroutineContext.Element? {
    if (ideStarter.isHeadless ||
        ideStarter.javaClass !in listOf(IdeStarter::class.java, IdeStarter.StandaloneLightEditStarter::class.java)) {
      channel.close()
      return null
    }
    channel.trySend(Event.IdeStarterStartedEvent)
    return MyMarker
  }

  suspend fun getStartUpContextElementToPass(): CoroutineContext.Element? {
    return if (isProperContext()) MyMarker else null
  }

  private fun reportViolation(violation: Violation) {
    channel.trySend(Event.ViolationEvent(violation))
    channel.close()
  }

  fun reportWelcomeScreenShown() {
    channel.trySend(Event.WelcomeScreenEvent())
  }

  suspend fun reportReopeningProjects(openPaths: List<*>) {
    if (!isProperContext()) return
    when (openPaths.size) {
      0 -> reportViolation(Violation.NoProjectFound)
      1 -> reportProjectType(ProjectsType.Reopened)
      else -> reportViolation(Violation.MultipleProjects)
    }
  }

  suspend fun reportProjectType(projectsType: ProjectsType) {
    if (!isProperContext()) return
    channel.trySend(Event.ProjectTypeReportEvent(projectsType))
  }

  /**
   * Reports the existence of project settings to filter cases of importing which may need more resources.
   */
  suspend fun reportProjectPath(projectFile: Path) {
    if (!isProperContext()) return
    val hasSettings = withContext(Dispatchers.IO) { ProjectUtilCore.isValidProjectPath(projectFile) }
    channel.trySend(Event.ProjectPathReportEvent(hasSettings))
  }

  suspend fun resetProjectPath() {
    if (!isProperContext()) return
    channel.trySend(Event.ResetProjectPathEvent)
  }

  suspend fun openingMultipleProjects() {
    if (!isProperContext()) return
    reportViolation(Violation.MultipleProjects)
  }

  suspend fun reportAlreadyOpenedProject() {
    if (!isProperContext()) return
    reportViolation(Violation.HasOpenedProject)
  }

  fun noProjectFound() {
    reportViolation(Violation.NoProjectFound)
  }

  fun lightEditProjectFound() {
    reportViolation(Violation.MightBeLightEditProject)
  }

  suspend fun reportUriOpening() {
    if (!isProperContext()) return
    reportViolation(Violation.OpeningURI)
  }

  fun reportStarterUsed() {
    reportViolation(Violation.ApplicationStarter)
  }

  fun frameBecameVisible() {
    channel.trySend(Event.FrameBecameVisibleEvent())
  }

  fun reportFrameBecameInteractive() {
    channel.trySend(Event.FrameBecameInteractiveEvent())
  }

  fun markupRestored(file: VirtualFileWithId) {
    channel.trySend(Event.MarkupRestoredEvent(file.id))
  }

  fun firstOpenedEditor(file: VirtualFile) {
    if (!currentThreadContext().isProperContext()) {
      return
    }
    channel.trySend(Event.FirstEditorEvent(SourceOfSelectedEditor.TextEditor, file, System.nanoTime()))
  }

  suspend fun firstOpenedUnknownEditor(file: VirtualFile, nanoTime: Long) {
    if (!isProperContext()) return
    channel.trySend(Event.FirstEditorEvent(SourceOfSelectedEditor.UnknownEditor, file, nanoTime))
  }

  suspend fun openedReadme(readmeFile: VirtualFile, nanoTime: Long) {
    if (!isProperContext()) return
    channel.trySend(Event.FirstEditorEvent(SourceOfSelectedEditor.FoundReadmeFile, readmeFile, nanoTime))
  }

  fun reportNoMoreEditorsOnStartup(nanoTime: Long) {
    if (!currentThreadContext().isProperContext()) {
      return
    }
    channel.trySend(Event.NoMoreEditorsEvent(nanoTime))
  }

  private data class LastHandledEvent(val event: Event.FUSReportableEvent, val durationReportedToFUS: Duration)

  private fun applyFrameVisibleEventIfPossible(
    afterSplash: Boolean,
    splashBecameVisibleEvent: Event.SplashBecameVisibleEvent?,
    frameBecameVisibleEvent: Event.FrameBecameVisibleEvent?,
    projectTypeReportEvent: Event.ProjectTypeReportEvent?,
    projectPathReportEvent: Event.ProjectPathReportEvent?,
    ideStarterStartedEvent: Event.IdeStarterStartedEvent?,
    lastHandledEvent: LastHandledEvent?,
  ): LastHandledEvent? {
    if (!afterSplash && splashBecameVisibleEvent != null &&
        (frameBecameVisibleEvent == null || splashBecameVisibleEvent.time <= frameBecameVisibleEvent.time)) {
      val durationFromStart = getDurationFromStart(splashBecameVisibleEvent.time, lastHandledEvent)
      FIRST_UI_SHOWN_EVENT.log(durationFromStart, UIResponseType.Splash)
      return LastHandledEvent(splashBecameVisibleEvent, durationFromStart)
    }

    if (frameBecameVisibleEvent != null) {
      if (ideStarterStartedEvent == null) throw CancellationException()
      val durationFromStart = getDurationFromStart(frameBecameVisibleEvent.time, lastHandledEvent)
      if (!afterSplash) {
        FIRST_UI_SHOWN_EVENT.log(durationFromStart, UIResponseType.Frame)
      }
      val projectsType = projectTypeReportEvent?.projectsType ?: ProjectsType.Unknown
      val settingsExist = projectPathReportEvent?.hasSettings
      if (settingsExist == null) {
        FRAME_BECAME_VISIBLE_EVENT.log(DURATION.with(durationFromStart), PROJECTS_TYPE.with(projectsType))
      }
      else {
        FRAME_BECAME_VISIBLE_EVENT.log(DURATION.with(durationFromStart), PROJECTS_TYPE.with(projectsType),
                                       HAS_SETTINGS.with(settingsExist))
      }
      return LastHandledEvent(frameBecameVisibleEvent, durationFromStart)
    }
    return null
  }

  private fun reportViolation(
    violation: Violation,
    time: Long,
    ideStarterStartedEvent: Event.IdeStarterStartedEvent?,
    lastHandledEvent: LastHandledEvent?,
  ): Nothing {
    val duration = getDurationFromStart(time, lastHandledEvent)
    if ((lastHandledEvent == null || lastHandledEvent.event is Event.SplashBecameVisibleEvent) && (ideStarterStartedEvent != null)) {
      if (lastHandledEvent == null) {
        FIRST_UI_SHOWN_EVENT.log(duration, UIResponseType.Frame)
      }
      FRAME_BECAME_VISIBLE_EVENT.log(DURATION.with(duration), VIOLATION.with(violation))
    }
    throw CancellationException()
  }

  private fun applyFrameInteractiveEventIfPossible(
    frameBecameInteractiveEvent: Event.FrameBecameInteractiveEvent?,
    lastHandledEvent: LastHandledEvent
  ): LastHandledEvent? {
    if (frameBecameInteractiveEvent != null) {
      val durationFromStart = getDurationFromStart(frameBecameInteractiveEvent.time, lastHandledEvent)
      FRAME_BECAME_INTERACTIVE_EVENT.log(durationFromStart)
      return LastHandledEvent(frameBecameInteractiveEvent, durationFromStart)
    }
    return null
  }

  private suspend fun applyEditorEventIfPossible(
    firstEditorEvent: Event.FirstEditorEvent?,
    noEditorEvent: Event.NoMoreEditorsEvent?,
    markupResurrectedFileIds: IntOpenHashSet,
    projectPathReportEvent: Event.ProjectPathReportEvent?,
    lastHandledEvent: LastHandledEvent,
  ) {
    if (firstEditorEvent == null && noEditorEvent == null) {
      return
    }

    val data: MutableList<EventPair<*>> = if (projectPathReportEvent != null) {
      mutableListOf(HAS_SETTINGS.with(projectPathReportEvent.hasSettings))
    }
    else {
      mutableListOf()
    }

    if (firstEditorEvent != null && (noEditorEvent == null || firstEditorEvent.time <= noEditorEvent.time)) {
      val file = firstEditorEvent.file
      val hasLoadedMarkup = file is VirtualFileWithId && markupResurrectedFileIds.contains(file.id)
      val fileType = readAction { file.fileType }
      ContainerUtil.addAll(data,
                           DURATION.with(getDurationFromStart(firstEditorEvent.time, lastHandledEvent)),
                           EventFields.FileType.with(fileType),
                           LOADED_CACHED_MARKUP_FIELD.with(hasLoadedMarkup),
                           NO_EDITORS_TO_OPEN_FIELD.with(false),
                           SOURCE_OF_SELECTED_EDITOR_FIELD.with(firstEditorEvent.sourceOfSelectedEditor))

    }
    else if (noEditorEvent != null) {
      ContainerUtil.addAll(data,
                           DURATION.with(getDurationFromStart(noEditorEvent.time, lastHandledEvent)),
                           NO_EDITORS_TO_OPEN_FIELD.with(true))
    }

    CODE_LOADED_AND_VISIBLE_IN_EDITOR_EVENT.log(data)
    throw CancellationException()
  }

  suspend fun startWritingStatistics() {
    try {
      handleStatisticEvents()
    }
    finally {
      channel.cancel()
      statsIsWritten = true
    }
  }

  @TestOnly
  fun isHandlingFinished(): Boolean {
    return statsIsWritten
  }

  private suspend fun handleStatisticEvents() {
    val markupResurrectedFileIds = IntOpenHashSet()
    var lastHandledEvent: LastHandledEvent? = null
    var ideStarterStartedEvent: Event.IdeStarterStartedEvent? = null
    var splashBecameVisibleEvent: Event.SplashBecameVisibleEvent? = null
    var frameBecameVisibleEvent: Event.FrameBecameVisibleEvent? = null
    var frameBecameInteractiveEvent: Event.FrameBecameInteractiveEvent? = null
    var projectPathReportEvent: Event.ProjectPathReportEvent? = null
    var projectTypeReportEvent: Event.ProjectTypeReportEvent? = null
    var firstEditorEvent: Event.FirstEditorEvent? = null
    var noEditorEvent: Event.NoMoreEditorsEvent? = null

    for (event in channel) {
      yield()
      when (event) {
        is Event.IdeStarterStartedEvent -> ideStarterStartedEvent = event
        is Event.SplashBecameVisibleEvent -> splashBecameVisibleEvent = event
        is Event.FrameBecameVisibleEvent -> {
          frameBecameVisibleEvent = event
        }
        is Event.WelcomeScreenEvent -> {
          val welcomeScreedDurationForFUS = getDurationFromStart(event.time, lastHandledEvent)
          if (splashBecameVisibleEvent == null) {
            WELCOME_SCREEN_EVENT.log(DURATION.with(welcomeScreedDurationForFUS), SPLASH_SCREEN_WAS_SHOWN.with(false))
          }
          else {
            val splashScreenFUSDuration = getDurationFromStart(splashBecameVisibleEvent.time, lastHandledEvent)
            WELCOME_SCREEN_EVENT.log(DURATION.with(welcomeScreedDurationForFUS), SPLASH_SCREEN_WAS_SHOWN.with(true),
                                     SPLASH_SCREEN_VISIBLE_DURATION.with(splashScreenFUSDuration))
          }
          reportViolation(Violation.WelcomeScreenShown, event.time, ideStarterStartedEvent, lastHandledEvent)
        }
        is Event.FrameBecameInteractiveEvent -> {
          frameBecameInteractiveEvent = event
        }
        is Event.MarkupRestoredEvent -> markupResurrectedFileIds.add(event.fileId)
        is Event.ProjectPathReportEvent -> if (projectPathReportEvent == null) projectPathReportEvent = event
        Event.ResetProjectPathEvent -> projectPathReportEvent = null
        is Event.ProjectTypeReportEvent -> if (projectTypeReportEvent == null) projectTypeReportEvent = event
        is Event.ViolationEvent -> reportViolation(event.violation, event.time, ideStarterStartedEvent, lastHandledEvent)
        is Event.FirstEditorEvent -> if (firstEditorEvent == null) firstEditorEvent = event
        is Event.NoMoreEditorsEvent -> if (noEditorEvent == null) noEditorEvent = event
      }

      while (true) {
        val newLastHandledEvent: LastHandledEvent? = when (lastHandledEvent?.event) {
          null ->
            applyFrameVisibleEventIfPossible(false, splashBecameVisibleEvent, frameBecameVisibleEvent, projectTypeReportEvent,
                                             projectPathReportEvent, ideStarterStartedEvent, lastHandledEvent = null)
          is Event.SplashBecameVisibleEvent ->
            applyFrameVisibleEventIfPossible(true, splashBecameVisibleEvent, frameBecameVisibleEvent, projectTypeReportEvent,
                                             projectPathReportEvent, ideStarterStartedEvent, lastHandledEvent)
          is Event.FrameBecameVisibleEvent -> applyFrameInteractiveEventIfPossible(frameBecameInteractiveEvent, lastHandledEvent)
          is Event.FrameBecameInteractiveEvent -> {
            applyEditorEventIfPossible(firstEditorEvent, noEditorEvent, markupResurrectedFileIds, projectPathReportEvent, lastHandledEvent)
            break
          }
        }
        if (newLastHandledEvent != null) {
          lastHandledEvent = newLastHandledEvent
        }
        else {
          break
        }
      }
    }
  }

  private object MyMarker : CoroutineContext.Key<MyMarker>, CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
      get() = this
  }

  private fun getDurationFromStart(finishTimestampNano: Long = System.nanoTime(),
                                   lastReportedEvent: LastHandledEvent?): Duration {
    val duration = (finishTimestampNano - StartUpMeasurer.getStartTime()).toDuration(DurationUnit.NANOSECONDS)
    return if (lastReportedEvent == null) duration else ComparatorUtil.max(duration, lastReportedEvent.durationReportedToFUS)
  }
}

private val WELCOME_SCREEN_GROUP = EventLogGroup("welcome.screen.startup.performance", 1)

private val SPLASH_SCREEN_WAS_SHOWN = EventFields.Boolean("splash_screen_was_shown")
private val SPLASH_SCREEN_VISIBLE_DURATION = createDurationField(DurationUnit.MILLISECONDS, "splash_screen_became_visible_duration_ms")
private val DURATION = createDurationField(DurationUnit.MILLISECONDS, "duration_ms")
private val WELCOME_SCREEN_EVENT = WELCOME_SCREEN_GROUP.registerVarargEvent("welcome.screen.shown",
                                                                            DURATION, SPLASH_SCREEN_WAS_SHOWN,
                                                                            SPLASH_SCREEN_VISIBLE_DURATION)

class WelcomeScreenPerformanceCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = WELCOME_SCREEN_GROUP
}

private val GROUP = EventLogGroup("reopen.project.startup.performance", 1)

private enum class UIResponseType {
  Splash,
  Frame,
}

private val UI_RESPONSE_TYPE = EventFields.Enum("type", UIResponseType::class.java)
private val FIRST_UI_SHOWN_EVENT: EventId2<Duration, UIResponseType> = GROUP.registerEvent("first.ui.shown", DURATION, UI_RESPONSE_TYPE)

private val PROJECTS_TYPE: EnumEventField<FUSProjectHotStartUpMeasurer.ProjectsType> =
  EventFields.Enum("projects_type", FUSProjectHotStartUpMeasurer.ProjectsType::class.java)
private val HAS_SETTINGS: BooleanEventField = EventFields.Boolean("has_settings")
private val VIOLATION: EnumEventField<FUSProjectHotStartUpMeasurer.Violation> =
  EventFields.Enum("violation", FUSProjectHotStartUpMeasurer.Violation::class.java)
private val FRAME_BECAME_VISIBLE_EVENT = GROUP.registerVarargEvent("frame.became.visible",
                                                                   DURATION, HAS_SETTINGS, PROJECTS_TYPE, VIOLATION)

private val FRAME_BECAME_INTERACTIVE_EVENT = GROUP.registerEvent("frame.became.interactive", DURATION)

private enum class SourceOfSelectedEditor {
  TextEditor,
  UnknownEditor,
  FoundReadmeFile,
}

private val LOADED_CACHED_MARKUP_FIELD = EventFields.Boolean("loaded_cached_markup")
private val SOURCE_OF_SELECTED_EDITOR_FIELD: EnumEventField<SourceOfSelectedEditor> =
  EventFields.Enum("source_of_selected_editor", SourceOfSelectedEditor::class.java)
private val NO_EDITORS_TO_OPEN_FIELD = EventFields.Boolean("no_editors_to_open")
private val CODE_LOADED_AND_VISIBLE_IN_EDITOR_EVENT = GROUP.registerVarargEvent("code.loaded.and.visible.in.editor",
                                                                                DURATION,
                                                                                EventFields.FileType,
                                                                                HAS_SETTINGS,
                                                                                LOADED_CACHED_MARKUP_FIELD,
                                                                                NO_EDITORS_TO_OPEN_FIELD, SOURCE_OF_SELECTED_EDITOR_FIELD)

internal class HotProjectReopenStartUpPerformanceCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}

@Suppress("unused")
@TestOnly
@Service(Service.Level.APP)
class FUSProjectHotStartUpMeasurerService {
  @TestOnly
  fun isHandlingFinished(): Boolean {
    return statsIsWritten
  }
}