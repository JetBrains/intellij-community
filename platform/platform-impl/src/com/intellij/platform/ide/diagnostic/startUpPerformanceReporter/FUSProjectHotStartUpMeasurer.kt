// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.diagnostic.startUpPerformanceReporter

import com.intellij.concurrency.currentThreadContext
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.idea.IdeStarter
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.containers.ContainerUtil
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Internal
object FUSProjectHotStartUpMeasurer {
  private val channel = Channel<Event>(Int.MAX_VALUE)

  enum class ProjectsType {
    Reopened,
    FromFilesToLoad, //see com.intellij.idea.IdeStarter.filesToLoad
    FromArgs,
    Unknown
  }

  enum class Violation {
    MightBeLightEditProject,
    MultipleProjects,
    NoProjectFound,
    WelcomeScreenShown,
    OpeningURI,               //see com.intellij.idea.IdeStarter.uriToOpen
    ApplicationStarter,
    HasOpenedProject
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
      val file: VirtualFile
    ) : Event {
      val time: Long = System.nanoTime()
    }

    class NoMoreEditorsEvent : Event {
      val time: Long = System.nanoTime()
    }

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

  private suspend fun reportFirstEditor(file: VirtualFile, sourceOfSelectedEditor: SourceOfSelectedEditor) {
    if (!isProperContext()) return
    channel.trySend(Event.FirstEditorEvent(sourceOfSelectedEditor, file))
  }

  fun firstOpenedEditor(file: VirtualFile) {
    if (!currentThreadContext().isProperContext()) {
      return
    }
    service<MeasurerCoroutineService>().coroutineScope.launch(context = MyMarker) {
      reportFirstEditor(file, SourceOfSelectedEditor.TextEditor)
    }
  }

  suspend fun firstOpenedUnknownEditor(file: VirtualFile) {
    reportFirstEditor(file, SourceOfSelectedEditor.UnknownEditor)
  }

  suspend fun openedReadme(readmeFile: VirtualFile) {
    reportFirstEditor(readmeFile, SourceOfSelectedEditor.FoundReadmeFile)
  }

  suspend fun reportNoMoreEditorsOnStartup() {
    if (!isProperContext()) return
    channel.trySend(Event.NoMoreEditorsEvent())
  }

  private fun applyFrameVisibleEventIfPossible(
    afterSplash: Boolean,
    splashBecameVisibleEvent: Event.SplashBecameVisibleEvent?,
    frameBecameVisibleEvent: Event.FrameBecameVisibleEvent?,
    projectTypeReportEvent: Event.ProjectTypeReportEvent?,
    projectPathReportEvent: Event.ProjectPathReportEvent?,
    ideStarterStartedEvent: Event.IdeStarterStartedEvent?,
    stop: () -> Unit
  ): Event.FUSReportableEvent? {
    if (!afterSplash && splashBecameVisibleEvent != null &&
        (frameBecameVisibleEvent == null || splashBecameVisibleEvent.time <= frameBecameVisibleEvent.time)) {
      FIRST_UI_SHOWN_EVENT.log(getDurationFromStart(splashBecameVisibleEvent.time).getValueForFUS(), UIResponseType.Splash)
      return splashBecameVisibleEvent
    }

    if (frameBecameVisibleEvent != null) {
      if (ideStarterStartedEvent == null) stop()
      val durationForFUS = getDurationFromStart(frameBecameVisibleEvent.time).getValueForFUS()
      if (!afterSplash) {
        FIRST_UI_SHOWN_EVENT.log(durationForFUS, UIResponseType.Frame)
      }
      val projectsType = projectTypeReportEvent?.projectsType ?: ProjectsType.Unknown
      val settingsExist = projectPathReportEvent?.hasSettings
      if (settingsExist == null) {
        FRAME_BECAME_VISIBLE_EVENT.log(DURATION.with(durationForFUS), PROJECTS_TYPE.with(projectsType))
      }
      else {
        FRAME_BECAME_VISIBLE_EVENT.log(DURATION.with(durationForFUS), PROJECTS_TYPE.with(projectsType),
                                       HAS_SETTINGS.with(settingsExist))
      }
      return frameBecameVisibleEvent
    }
    return null
  }

  private fun reportViolation(
    violation: Violation,
    time: Long,
    ideStarterStartedEvent: Event.IdeStarterStartedEvent?,
    lastHandledEvent: Event.FUSReportableEvent?,
    stop: () -> Unit
  ) {
    val duration = getDurationFromStart(time).getValueForFUS()
    if ((lastHandledEvent == null || lastHandledEvent is Event.SplashBecameVisibleEvent) && (ideStarterStartedEvent != null)) {
      if (lastHandledEvent == null) {
        FIRST_UI_SHOWN_EVENT.log(duration, UIResponseType.Frame)
      }
      FRAME_BECAME_VISIBLE_EVENT.log(DURATION.with(duration), VIOLATION.with(violation))
    }
    stop()
  }

  private fun applyFrameInteractiveEventIfPossible(
    frameBecameInteractiveEvent: Event.FrameBecameInteractiveEvent?
  ): Event.FUSReportableEvent? {
    if (frameBecameInteractiveEvent != null) {
      FRAME_BECAME_INTERACTIVE_EVENT.log(getDurationFromStart(frameBecameInteractiveEvent.time).getValueForFUS())
      return frameBecameInteractiveEvent
    }
    return null
  }

  private suspend fun applyEditorEventIfPossible(
    firstEditorEvent: Event.FirstEditorEvent?,
    noEditorEvent: Event.NoMoreEditorsEvent?,
    markupResurrectedFileIds: IntOpenHashSet,
    projectPathReportEvent: Event.ProjectPathReportEvent?,
    stop: () -> Unit
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
                           DURATION.with(getDurationFromStart(firstEditorEvent.time).getValueForFUS()),
                           EventFields.FileType.with(fileType),
                           LOADED_CACHED_MARKUP_FIELD.with(hasLoadedMarkup),
                           NO_EDITORS_TO_OPEN_FIELD.with(false),
                           SOURCE_OF_SELECTED_EDITOR_FIELD.with(firstEditorEvent.sourceOfSelectedEditor))

    }
    else if (noEditorEvent != null) {
      ContainerUtil.addAll(data,
                           DURATION.with(getDurationFromStart(noEditorEvent.time).getValueForFUS()),
                           NO_EDITORS_TO_OPEN_FIELD.with(true))
    }

    CODE_LOADED_AND_VISIBLE_IN_EDITOR_EVENT.log(data)
    stop()
  }

  suspend fun startWritingStatistics() {
    val markupResurrectedFileIds = IntOpenHashSet()
    var lastHandledEvent: Event.FUSReportableEvent? = null
    var ideStarterStartedEvent: Event.IdeStarterStartedEvent? = null
    var splashBecameVisibleEvent: Event.SplashBecameVisibleEvent? = null
    var frameBecameVisibleEvent: Event.FrameBecameVisibleEvent? = null
    var frameBecameInteractiveEvent: Event.FrameBecameInteractiveEvent? = null
    var projectPathReportEvent: Event.ProjectPathReportEvent? = null
    var projectTypeReportEvent: Event.ProjectTypeReportEvent? = null
    var firstEditorEvent: Event.FirstEditorEvent? = null
    var noEditorEvent: Event.NoMoreEditorsEvent? = null
    var isChannelHandlingStopped = false

    fun stop() {
      isChannelHandlingStopped = true
      channel.close()
    }

    for (event in channel) {
      if (isChannelHandlingStopped) continue
      when (event) {
        is Event.IdeStarterStartedEvent -> ideStarterStartedEvent = event
        is Event.SplashBecameVisibleEvent -> splashBecameVisibleEvent = event
        is Event.FrameBecameVisibleEvent -> {
          frameBecameVisibleEvent = event
        }
        is Event.WelcomeScreenEvent -> {
          val welcomeScreedDurationForFUS = getDurationFromStart(event.time).getValueForFUS()
          if (splashBecameVisibleEvent == null) {
            WELCOME_SCREEN_EVENT.log(DURATION.with(welcomeScreedDurationForFUS), SPLASH_SCREEN_WAS_SHOWN.with(false))
          }
          else {
            val splashScreenFUSDuration = getDurationFromStart(splashBecameVisibleEvent.time).getValueForFUS()
            WELCOME_SCREEN_EVENT.log(DURATION.with(welcomeScreedDurationForFUS), SPLASH_SCREEN_WAS_SHOWN.with(true),
                                     SPLASH_SCREEN_VISIBLE_DURATION.with(splashScreenFUSDuration))
          }
          reportViolation(Violation.WelcomeScreenShown, event.time, ideStarterStartedEvent, lastHandledEvent, ::stop)
        }
        is Event.FrameBecameInteractiveEvent -> {
          frameBecameInteractiveEvent = event
        }
        is Event.MarkupRestoredEvent -> markupResurrectedFileIds.add(event.fileId)
        is Event.ProjectPathReportEvent -> if (projectPathReportEvent == null) projectPathReportEvent = event
        Event.ResetProjectPathEvent -> projectPathReportEvent = null
        is Event.ProjectTypeReportEvent -> if (projectTypeReportEvent == null) projectTypeReportEvent = event
        is Event.ViolationEvent -> {
          reportViolation(event.violation, event.time, ideStarterStartedEvent, lastHandledEvent, ::stop)
        }
        is Event.FirstEditorEvent -> if (firstEditorEvent == null) firstEditorEvent = event
        is Event.NoMoreEditorsEvent -> if (noEditorEvent == null) noEditorEvent = event
      }

      if (isChannelHandlingStopped) continue

      while (true) {
        val newLastHandledEvent: Event.FUSReportableEvent? = when (lastHandledEvent) {
          null ->
            applyFrameVisibleEventIfPossible(false, splashBecameVisibleEvent, frameBecameVisibleEvent, projectTypeReportEvent,
                                             projectPathReportEvent, ideStarterStartedEvent, ::stop)
          is Event.SplashBecameVisibleEvent ->
            applyFrameVisibleEventIfPossible(true, splashBecameVisibleEvent, frameBecameVisibleEvent, projectTypeReportEvent,
                                             projectPathReportEvent, ideStarterStartedEvent, ::stop)
          is Event.FrameBecameVisibleEvent -> applyFrameInteractiveEventIfPossible(frameBecameInteractiveEvent)
          is Event.FrameBecameInteractiveEvent -> {
            applyEditorEventIfPossible(firstEditorEvent, noEditorEvent, markupResurrectedFileIds, projectPathReportEvent, ::stop)
            null
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
}

private fun getDurationFromStart(finishTimestampNano: Long = System.nanoTime()): Duration {
  return (finishTimestampNano - StartUpMeasurer.getStartTime()).toDuration(DurationUnit.NANOSECONDS)
}

private fun Duration.getValueForFUS(): Long {
  return inWholeMilliseconds
}

private val WELCOME_SCREEN_GROUP = EventLogGroup("welcome.screen.startup.performance", 1)

private val SPLASH_SCREEN_WAS_SHOWN = EventFields.Boolean("splash_screen_was_shown")
private val SPLASH_SCREEN_VISIBLE_DURATION = LongEventField("splash_screen_became_visible_duration_ms")
private val DURATION = EventFields.DurationMs
private val WELCOME_SCREEN_EVENT = WELCOME_SCREEN_GROUP.registerVarargEvent("welcome.screen.shown",
                                                                            DURATION, SPLASH_SCREEN_WAS_SHOWN,
                                                                            SPLASH_SCREEN_VISIBLE_DURATION)

class WelcomeScreenPerformanceCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = WELCOME_SCREEN_GROUP
}

private val GROUP = EventLogGroup("reopen.project.startup.performance", 1)

private enum class UIResponseType {
  Splash, Frame
}

private val UI_RESPONSE_TYPE = EventFields.Enum("type", UIResponseType::class.java)
private val FIRST_UI_SHOWN_EVENT: EventId2<Long, UIResponseType> = GROUP.registerEvent("first.ui.shown", DURATION, UI_RESPONSE_TYPE)

private val PROJECTS_TYPE: EnumEventField<FUSProjectHotStartUpMeasurer.ProjectsType> =
  EventFields.Enum("projects_type", FUSProjectHotStartUpMeasurer.ProjectsType::class.java)
private val HAS_SETTINGS: BooleanEventField = EventFields.Boolean("has_settings")
private val VIOLATION: EnumEventField<FUSProjectHotStartUpMeasurer.Violation> =
  EventFields.Enum("violation", FUSProjectHotStartUpMeasurer.Violation::class.java)
private val FRAME_BECAME_VISIBLE_EVENT = GROUP.registerVarargEvent("frame.became.visible",
                                                                   DURATION, HAS_SETTINGS, PROJECTS_TYPE, VIOLATION)

private val FRAME_BECAME_INTERACTIVE_EVENT = GROUP.registerEvent("frame.became.interactive", DURATION)

private enum class SourceOfSelectedEditor {
  TextEditor, UnknownEditor, FoundReadmeFile
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

@Service
private class MeasurerCoroutineService(val coroutineScope: CoroutineScope)