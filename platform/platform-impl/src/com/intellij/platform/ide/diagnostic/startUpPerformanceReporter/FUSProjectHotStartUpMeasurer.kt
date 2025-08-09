// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.diagnostic.startUpPerformanceReporter

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.concurrency.currentThreadContext
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.impl.ProjectUtil
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.events.EventFields.createDurationField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.zombie.SpawnRecipe
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.FileIdAdapter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer.MarkupType
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer.getStartUpContextElementIntoIdeStarter
import com.intellij.util.containers.ComparatorUtil
import com.intellij.util.containers.ContainerUtil
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration


@Volatile
private var statsIsWritten = false

@Internal
object FUSProjectHotStartUpMeasurer {
  private val channel = Channel<Event>(Int.MAX_VALUE)
  private val counter = AtomicInteger(0)

  private data class ProjectId(val projectOrder: Int) {
    constructor() : this(counter.incrementAndGet())
  }

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

  enum class MarkupType {
    HIGHLIGHTING,
    CODE_FOLDING,
    CODE_VISION,
    DECLARATIVE_HINTS,
    PARAMETER_HINTS,

    // this works for internal action ToggleFocusViewModeAction and is not worth testing or reporting FOCUS_MODE,
    DOC_RENDER,
    ;

    @Internal
    fun getFieldName(): String = getField(this).name
  }

  private fun CoroutineContext.isProperContext(): Boolean {
    return this[MyMarker] != null
  }

  private fun CoroutineContext.getProjectMarker(): MyProjectMarker? {
    return this[MyProjectMarker.Key]
  }

  private sealed interface Event {
    /**
     * It's an event that corresponds to a FUS event and does not stop handling of events.
     * See their list at https://youtrack.jetbrains.com/issue/IJPL-269
     */
    sealed interface FUSReportableEvent : Event

    sealed interface ProjectDependentEvent : Event {
      val projectId: ProjectId
    }

    class SplashBecameVisibleEvent : FUSReportableEvent {
      val time: Long = System.nanoTime()
    }

    class WelcomeScreenEvent : Event {
      val time: Long = System.nanoTime()
    }

    class ViolationEvent(val violation: Violation) : Event {
      init {
        thisLogger().assertTrue(violation != Violation.MultipleProjects, "Use `MultipleProjectsEvent` instead")
      }

      val time: Long = System.nanoTime()
    }

    class ProjectTypeReportEvent(val projectsType: ProjectsType) : Event
    class ProjectPathReportEvent(override val projectId: ProjectId, val hasSettings: Boolean) : ProjectDependentEvent
    class FrameBecameVisibleEvent(override val projectId: ProjectId) : FUSReportableEvent, ProjectDependentEvent {
      val time: Long = System.nanoTime()
    }

    class FrameBecameInteractiveEvent(override val projectId: ProjectId) : FUSReportableEvent, ProjectDependentEvent {
      val time: Long = System.nanoTime()
    }

    class MarkupRestoredEvent(val fileId: Int, val markupType: MarkupType) : Event

    class FirstEditorEvent(
      val sourceOfSelectedEditor: SourceOfSelectedEditor,
      val file: VirtualFile,
      val time: Long,
      override val projectId: ProjectId,
    ) : ProjectDependentEvent, FUSReportableEvent

    class NoMoreEditorsEvent(val time: Long, override val projectId: ProjectId) : FUSReportableEvent, ProjectDependentEvent

    data object IdeStarterStartedEvent : Event
    data class MultipleProjectsEvent(
      val reopening: Boolean,
      val numberOfProjects: Int,
      val hasLightEditProjects: Boolean,
      val time: Long = System.nanoTime(),
    ) : FUSReportableEvent
  }

  /**
   * Might happen before [getStartUpContextElementIntoIdeStarter]
   */
  fun splashBecameVisible() {
    channel.trySend(Event.SplashBecameVisibleEvent())
  }

  fun getStartUpContextElementIntoIdeStarter(close: Boolean): CoroutineContext.Element? {
    if (close) {
      statsIsWritten = true
      channel.close()
      return null
    }
    channel.trySend(Event.IdeStarterStartedEvent)
    return MyMarker
  }

  @JvmStatic
  fun getStartUpContextElementToPass(): CoroutineContext? {
    val threadContext = currentThreadContext()
    if (!threadContext.isProperContext()) return null
    val projectMarker = threadContext.getProjectMarker()
    if (projectMarker == null) {
      return MyMarker
    }
    return MyMarker + projectMarker
  }

  /**
   * Provides [CoroutineContext] with empty project marker used in later reporting;
   * used as a workaround when a scenario is known to be already violated (light edit),
   * or a way to provide a context element associated with the project is not yet implemented (rem dev).
   *
   * Allows proper reporting of FUS events for single non-light edit projects,
   * while attributing events to a single project in multiple project events.
   */
  // This code is necessary for reporting metrics from the frontend because frontend metrics are sent outside the project initialization process.
  @Internal
  fun getContextElementWithEmptyProjectElementToPass(): CoroutineContext {
    return MyMarker + EmptyProjectMarker
  }

  private fun reportViolation(violation: Violation) {
    if (violation == Violation.MultipleProjects) {
      thisLogger().error("Use `openingMultipleProjects()` instead")
    }
    else {
      channel.trySend(Event.ViolationEvent(violation))
      channel.close()
    }
  }

  fun reportWelcomeScreenShown() {
    channel.trySend(Event.WelcomeScreenEvent())
  }

  fun reportReopeningProjects(openPaths: List<*>) {
    if (!currentThreadContext().isProperContext()) return
    when (openPaths.size) {
      0 -> reportViolation(Violation.NoProjectFound)
      1 -> reportProjectType(ProjectsType.Reopened)
      // light edit files are not reopened
      else -> openingMultipleProjects(true, openPaths.size, false)
    }
  }

  fun reportProjectType(projectsType: ProjectsType) {
    if (!currentThreadContext().isProperContext()) return
    //too early for project distinction
    channel.trySend(Event.ProjectTypeReportEvent(projectsType))
  }

  /**
   * Invokes [block] in coroutine context with project marker used in later reporting;
   * reports the existence of project settings to filter cases of importing which may need more resources.
   */
  suspend fun <T> withProjectContextElement(projectFile: Path, block: suspend () -> T): T {
    if (!currentThreadContext().isProperContext()) {
      return block.invoke()
    }
    val projectId = ProjectId()
    val hasSettings = ProjectUtil.isValidProjectPath(projectFile)
    channel.trySend(Event.ProjectPathReportEvent(projectId, hasSettings))
    return withContext(MyProjectMarker(projectId)) {
      block.invoke()
    }
  }

  private fun withRequiredProjectMarker(block: (ProjectId) -> Unit) {
    if (!currentThreadContext().isProperContext()) {
      return
    }
    val projectMarker = currentThreadContext().getProjectMarker()
    if (projectMarker == null) {
      // Do not break a project opening of there is no marker
      thisLogger().error("No project marker found")
      return
    }
    block.invoke(projectMarker.id)
  }

  fun openingMultipleProjects(reopening: Boolean, numberOfProjects: Int, hasLightEditProjects: Boolean) {
    if (!currentThreadContext().isProperContext()) return
    channel.trySend(Event.MultipleProjectsEvent(reopening, numberOfProjects, hasLightEditProjects))
  }

  fun reportAlreadyOpenedProject() {
    if (!currentThreadContext().isProperContext()) return
    reportViolation(Violation.HasOpenedProject)
  }

  fun noProjectFound() {
    reportViolation(Violation.NoProjectFound)
  }

  fun lightEditProjectFound() {
    reportViolation(Violation.MightBeLightEditProject)
  }

  fun reportUriOpening() {
    if (!currentThreadContext().isProperContext()) return
    reportViolation(Violation.OpeningURI)
  }

  fun reportStarterUsed() {
    reportViolation(Violation.ApplicationStarter)
  }

  fun frameBecameVisible() {
    withRequiredProjectMarker { projectId ->
      channel.trySend(Event.FrameBecameVisibleEvent(projectId))
    }
  }

  fun reportFrameBecameInteractive() {
    withRequiredProjectMarker { projectId ->
      channel.trySend(Event.FrameBecameInteractiveEvent(projectId))
    }
  }

  fun markupRestored(recipe: SpawnRecipe, type: MarkupType) {
    channel.trySend(Event.MarkupRestoredEvent(recipe.fileId, type))
  }

  fun firstOpenedEditor(file: VirtualFile, project: Project) {
    withRequiredProjectMarker { projectId ->
      channel.trySend(Event.FirstEditorEvent(SourceOfSelectedEditor.TextEditor, file, System.nanoTime(), projectId))
      if (ApplicationManagerEx.isInIntegrationTest()) {
        val fileEditorManager = FileEditorManager.getInstance(project)
        checkEditorHasBasicHighlight(file, project, fileEditorManager)
      }
    }
  }

  /**
   * Unfortunately, the current architecture doesn't allow checking that there is basic highlighting (syntax plus maybe folding) in an editor.
   * Here are some heuristics that may save us from bugs, but that is not guaranteed.
   */
  private fun checkEditorHasBasicHighlight(file: VirtualFile, project: Project, fileEditorManager: FileEditorManager) {
    val textEditor: TextEditor = fileEditorManager.getEditors(file)[0] as TextEditor
    // It's marked @NotNull, but before initialization it is actually null.
    // So this is a valid check that highlighter is initialized. It is used for syntax highlighting
    // via HighlighterIterator from LexerEditorHighlighter.createIterator & IterationState.
    // See also: EditorHighlighterUpdater.updateHighlighters() and setupHighlighter(),
    // LexerEditorHighlighter.createIterator, TextEditorImplKt.setHighlighterToEditor
    textEditor.editor.highlighter
    // See usages of TextEditorImpl.asyncLoader in PsiAwareTextEditorImpl, especially in the span "HighlighterTextEditorInitializer".
    // It's reasonable to expect the loaded editor to provide minimal highlighting the statistic is interested in.
    if (!textEditor.isEditorLoaded) {
      thisLogger().error("The editor is not loaded yet")
    }

    val cachedDocument = FileDocumentManager.getInstance().getCachedDocument(file)
    if (cachedDocument == null) {
      thisLogger().error("No cached document for ${file.path}")
    }
    else {
      val markupModel = DocumentMarkupModel.forDocument(cachedDocument, project, false)
      if (markupModel == null) {
        thisLogger().error("No markup model for ${file.path} when the editor is opened")
      }
    }
  }

  fun firstOpenedUnknownEditor(file: VirtualFile, nanoTime: Long) {
    withRequiredProjectMarker { projectId ->
      channel.trySend(Event.FirstEditorEvent(SourceOfSelectedEditor.UnknownEditor, file, nanoTime, projectId))
      if (ApplicationManagerEx.isInIntegrationTest()) {
        val project = ProjectManager.getInstance().openProjects[0]
        val fileEditorManager = FileEditorManager.getInstance(project)
        checkEditorHasBasicHighlight(file, project, fileEditorManager)
      }
    }
  }

  fun openedReadme(readmeFile: VirtualFile, nanoTime: Long) {
    withRequiredProjectMarker { projectId ->
      channel.trySend(Event.FirstEditorEvent(SourceOfSelectedEditor.FoundReadmeFile, readmeFile, nanoTime, projectId))
      // Do not check highlights here, because the readme file is opened in preview-only mode with
      // `readme.putUserData(TextEditorWithPreview.DEFAULT_LAYOUT_FOR_FILE, TextEditorWithPreview.Layout.SHOW_PREVIEW)`,
      // see the caller
    }
  }

  fun reportNoMoreEditorsOnStartup(nanoTime: Long) {
    withRequiredProjectMarker { projectId ->
      channel.trySend(Event.NoMoreEditorsEvent(nanoTime, projectId))
    }
  }

  private data class LastHandledEvent(val event: Event.FUSReportableEvent, val durationReportedToFUS: Duration)

  private fun reportViolation(
    violation: Violation,
    time: Long,
    ideStarterStartedEvent: Event.IdeStarterStartedEvent?,
    reportedFirstUiShownEvent: LastHandledEvent?,
  ): Duration {
    val duration = getDurationFromStart(time, reportedFirstUiShownEvent)
    if ((reportedFirstUiShownEvent == null || reportedFirstUiShownEvent.event is Event.SplashBecameVisibleEvent) && (ideStarterStartedEvent != null)) {
      if (reportedFirstUiShownEvent == null) {
        FIRST_UI_SHOWN_EVENT.log(duration, UIResponseType.Frame)
      }
      FRAME_BECAME_VISIBLE_EVENT.log(DURATION.with(duration), VIOLATION.with(violation))
    }
    return duration
  }

  @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
  suspend fun startWritingStatistics() {
    val counterContext = newSingleThreadContext("HandlingStartupEventsContext")
    try {
      withContext(counterContext) {
        handleStatisticEvents()
      }
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

  private class MarkupResurrectedFileIds {
    private val ids: Map<MarkupType, IntSet> = MarkupType.entries.associateWith { IntOpenHashSet() }

    fun addId(fileId: Int, markupType: MarkupType) {
      ids[markupType]!!.add(fileId)
    }

    fun contains(file: VirtualFile, markupType: MarkupType): Boolean {
      val fileId = FileIdAdapter.getInstance().getId(file) ?: return false
      return ids[markupType]!!.contains(fileId)
    }
  }

  // Runs on a single thread, see `startWritingStatistics`
  private suspend fun handleStatisticEvents() {
    val markupResurrectedFileIds = MarkupResurrectedFileIds()
    var ideStarterStartedEvent: Event.IdeStarterStartedEvent? = null
    var splashBecameVisibleEvent: Event.SplashBecameVisibleEvent? = null
    var multipleProjectsOpenedEvent: Event.MultipleProjectsEvent? = null
    val frameBecameVisibleEventMap: MutableMap<ProjectId, Event.FrameBecameVisibleEvent> = mutableMapOf()
    val frameBecameInteractiveEventMap: MutableMap<ProjectId, Event.FrameBecameInteractiveEvent> = mutableMapOf()
    val projectPathReportEvents: MutableMap<ProjectId, Event.ProjectPathReportEvent> = mutableMapOf()
    var projectTypeReportEvent: Event.ProjectTypeReportEvent? = null
    val firstEditorEventMap: MutableMap<ProjectId, Event.FirstEditorEvent> = mutableMapOf()
    val noEditorEventMap: MutableMap<ProjectId, Event.NoMoreEditorsEvent> = mutableMapOf()

    var reportedFirstUiShownEvent: LastHandledEvent? = null
    val reportedFrameBecameVisibleEventMap: MutableMap<ProjectId, LastHandledEvent> = mutableMapOf()
    val reportedFrameBecameInteractiveEvenMap: MutableMap<ProjectId, LastHandledEvent> = mutableMapOf()
    val reportedFirstEditorEvenMap: MutableMap<ProjectId, LastHandledEvent> = mutableMapOf()

    fun <V : Event.ProjectDependentEvent> MutableMap<ProjectId, V>.putIfAbsent(event: V) {
      putIfAbsent(event.projectId, event)
    }

    for (event in channel) {
      yield()
      when (event) {
        is Event.IdeStarterStartedEvent -> ideStarterStartedEvent = event
        is Event.SplashBecameVisibleEvent -> splashBecameVisibleEvent = event
        is Event.FrameBecameVisibleEvent -> {
          frameBecameVisibleEventMap.putIfAbsent(event)
        }
        is Event.WelcomeScreenEvent -> {
          val welcomeScreedDurationForFUS = getDurationFromStart(event.time, reportedFirstUiShownEvent)
          if (splashBecameVisibleEvent == null) {
            WELCOME_SCREEN_EVENT.log(DURATION.with(welcomeScreedDurationForFUS), SPLASH_SCREEN_WAS_SHOWN.with(false))
          }
          else {
            val splashScreenFUSDuration = getDurationFromStart(splashBecameVisibleEvent.time, reportedFirstUiShownEvent)
            WELCOME_SCREEN_EVENT.log(DURATION.with(welcomeScreedDurationForFUS), SPLASH_SCREEN_WAS_SHOWN.with(true),
                                     SPLASH_SCREEN_VISIBLE_DURATION.with(splashScreenFUSDuration))
          }
          reportViolation(Violation.WelcomeScreenShown, event.time, ideStarterStartedEvent, reportedFirstUiShownEvent)
          throw CancellationException()
        }
        is Event.FrameBecameInteractiveEvent -> {
          frameBecameInteractiveEventMap.putIfAbsent(event)
        }
        is Event.MarkupRestoredEvent -> markupResurrectedFileIds.addId(event.fileId, event.markupType)
        is Event.ProjectPathReportEvent -> projectPathReportEvents.putIfAbsent(event)
        is Event.ProjectTypeReportEvent -> if (projectTypeReportEvent == null) projectTypeReportEvent = event
        is Event.ViolationEvent -> {
          reportViolation(event.violation, event.time, ideStarterStartedEvent, reportedFirstUiShownEvent)
          throw CancellationException()
        }
        is Event.MultipleProjectsEvent -> {
          multipleProjectsOpenedEvent = event
          val duration = reportViolation(Violation.MultipleProjects, event.time, ideStarterStartedEvent, reportedFirstUiShownEvent)
          if (reportedFirstUiShownEvent == null && ideStarterStartedEvent != null) {
            reportedFirstUiShownEvent = LastHandledEvent(event, duration)
          }
          if (multipleProjectsOpenedEvent.hasLightEditProjects) {
            for (value in 1..multipleProjectsOpenedEvent.numberOfProjects) {
              MULTIPLE_PROJECT_FRAME_BECAME_VISIBLE_EVENT.log(
                DURATION.with(duration),
                VIOLATION.with(Violation.MightBeLightEditProject),
                PROJECT_ORDER_FIELD.with(value),
                NUMBER_OF_PROJECTS_FIELD.with(multipleProjectsOpenedEvent.numberOfProjects)
              )
            }
            throw CancellationException()
          }
        }
        is Event.FirstEditorEvent -> firstEditorEventMap.putIfAbsent(event)
        is Event.NoMoreEditorsEvent -> noEditorEventMap.putIfAbsent(event)
      }

      while (true) {
        if (reportedFirstUiShownEvent == null) {
          if (splashBecameVisibleEvent != null) {
            val durationFromStart = getDurationFromStart(splashBecameVisibleEvent.time, null)
            FIRST_UI_SHOWN_EVENT.log(durationFromStart, UIResponseType.Splash)
            reportedFirstUiShownEvent = LastHandledEvent(splashBecameVisibleEvent, durationFromStart)
          }
          else if (multipleProjectsOpenedEvent == null && !frameBecameVisibleEventMap.isEmpty()) {
            val frameBecameVisibleEvent = frameBecameVisibleEventMap.values.first()
            val durationFromStart = getDurationFromStart(frameBecameVisibleEvent.time, null)
            FIRST_UI_SHOWN_EVENT.log(durationFromStart, UIResponseType.Frame)
            reportedFirstUiShownEvent = LastHandledEvent(frameBecameVisibleEvent, durationFromStart)
          }
          else {
            break
          }
        }

        if (multipleProjectsOpenedEvent != null && projectPathReportEvents.isEmpty()) {
          break
        }

        if (multipleProjectsOpenedEvent == null && reportedFrameBecameVisibleEventMap.isEmpty() && frameBecameVisibleEventMap.isEmpty()) {
          break
        }
        else if (frameBecameVisibleEventMap.size > reportedFrameBecameVisibleEventMap.size) {
          if (ideStarterStartedEvent == null) throw CancellationException()
          if (multipleProjectsOpenedEvent == null) {
            thisLogger().assertTrue(frameBecameVisibleEventMap.size == 1, "There are more than one frame became visible")
          }

          for ((projectId, frameBecameVisibleEvent) in frameBecameVisibleEventMap) {
            if (reportedFrameBecameVisibleEventMap.containsKey(projectId)) continue
            val durationFromStart = getDurationFromStart(frameBecameVisibleEvent.time, reportedFirstUiShownEvent)
            val projectsType = projectTypeReportEvent?.projectsType
                               ?: (if (multipleProjectsOpenedEvent?.reopening == true) ProjectsType.Reopened else ProjectsType.Unknown)
            val data: MutableList<EventPair<*>> = mutableListOf(DURATION.with(durationFromStart), PROJECTS_TYPE.with(projectsType))
            val settingsExist = projectPathReportEvents[projectId]?.hasSettings
            if (settingsExist != null) {
              data.add(HAS_SETTINGS.with(settingsExist))
            }

            if (multipleProjectsOpenedEvent == null) {
              FRAME_BECAME_VISIBLE_EVENT.log(data)
            }
            else {
              addMultipleProjectSpecificFields(data, projectId, multipleProjectsOpenedEvent)
              MULTIPLE_PROJECT_FRAME_BECAME_VISIBLE_EVENT.log(data)
            }
            reportedFrameBecameVisibleEventMap[projectId] = LastHandledEvent(frameBecameVisibleEvent, durationFromStart)
          }
        }

        if (multipleProjectsOpenedEvent == null && reportedFrameBecameInteractiveEvenMap.isEmpty() && frameBecameInteractiveEventMap.isEmpty()) {
          break
        }
        else if (frameBecameInteractiveEventMap.size > reportedFrameBecameInteractiveEvenMap.size) {
          for ((projectId, frameBecameInteractiveEvent) in frameBecameInteractiveEventMap) {
            if (reportedFrameBecameInteractiveEvenMap.containsKey(projectId)) continue
            val lastReportedEvent = reportedFrameBecameVisibleEventMap[projectId] ?: reportedFirstUiShownEvent
            val durationFromStart = getDurationFromStart(frameBecameInteractiveEvent.time, lastReportedEvent)

            if (multipleProjectsOpenedEvent == null) {
              FRAME_BECAME_INTERACTIVE_EVENT.log(durationFromStart)
            }
            else {
              MULTIPLE_PROJECT_FRAME_BECAME_INTERACTIVE_EVENT.log(durationFromStart, projectId.projectOrder, multipleProjectsOpenedEvent.numberOfProjects)
            }
            reportedFrameBecameInteractiveEvenMap[projectId] = LastHandledEvent(frameBecameInteractiveEvent, durationFromStart)
          }
        }

        // only the first editor is left to be reported
        if (projectPathReportEvents.size > reportedFirstEditorEvenMap.size) {
          for ((projectId, projectPathReportEvent) in projectPathReportEvents) {
            if (reportedFirstEditorEvenMap[projectId] != null) continue
            val lastReportedEvent = reportedFrameBecameInteractiveEvenMap[projectId] ?: reportedFrameBecameVisibleEventMap[projectId]
                                    ?: reportedFirstUiShownEvent
            val firstEditorEvent = firstEditorEventMap[projectId]
            val data: MutableList<EventPair<*>> = mutableListOf(HAS_SETTINGS.with(projectPathReportEvent.hasSettings))

            lateinit var lastHandledEvent: LastHandledEvent
            val noEditorEvent = noEditorEventMap[projectId]
            if (firstEditorEvent == null && noEditorEvent == null) {
              continue
            }
            else if (firstEditorEvent != null && (noEditorEvent == null || firstEditorEvent.time <= noEditorEvent.time)) {
              val file = firstEditorEvent.file
              val fileType = readAction { file.fileType }
              val duration = getDurationFromStart(firstEditorEvent.time, lastReportedEvent)
              lastHandledEvent = LastHandledEvent(firstEditorEvent, duration)
              ContainerUtil.addAll(data,
                                   DURATION.with(duration),
                                   EventFields.FileType.with(fileType),
                                   NO_EDITORS_TO_OPEN_FIELD.with(false),
                                   SOURCE_OF_SELECTED_EDITOR_FIELD.with(firstEditorEvent.sourceOfSelectedEditor))
              for (type in MarkupType.entries) {
                data.add(getField(type).with(markupResurrectedFileIds.contains(file, type)))
              }
            }
            else if (noEditorEvent != null) { //actually here always `noEditorEvent != null`, but Kotlin fails to understand it
              val duration = getDurationFromStart(noEditorEvent.time, lastReportedEvent)
              lastHandledEvent = LastHandledEvent(noEditorEvent, duration)
              ContainerUtil.addAll(data,
                                   DURATION.with(duration),
                                   NO_EDITORS_TO_OPEN_FIELD.with(true))
            }

            if (multipleProjectsOpenedEvent == null) {
              CODE_LOADED_AND_VISIBLE_IN_EDITOR_EVENT.log(data)
            }
            else {
              addMultipleProjectSpecificFields(data, projectId, multipleProjectsOpenedEvent)
              MULTIPLE_PROJECT_CODE_LOADED_AND_VISIBLE_IN_EDITOR_EVENT.log(data)
            }
            reportedFirstEditorEvenMap[projectId] = lastHandledEvent
          }
        }

        if (projectPathReportEvents.size > reportedFirstEditorEvenMap.size) {
          break
        }
        else {
          throw CancellationException()
        }
      }
    }
  }

  private fun addMultipleProjectSpecificFields(
    data: MutableList<EventPair<*>>,
    projectId: ProjectId,
    multipleProjectsOpenedEvent: Event.MultipleProjectsEvent,
  ) {
    data.add(PROJECT_ORDER_FIELD.with(projectId.projectOrder))
    data.add(NUMBER_OF_PROJECTS_FIELD.with(multipleProjectsOpenedEvent.numberOfProjects))
  }

  private object MyMarker : CoroutineContext.Key<MyMarker>, CoroutineContext.Element, IntelliJContextElement {
    override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

    override val key: CoroutineContext.Key<*>
      get() = this
  }

  private val EmptyProjectMarker = MyProjectMarker(ProjectId(-1))

  private data class MyProjectMarker(val id: ProjectId) : CoroutineContext.Element, IntelliJContextElement {
    object Key : CoroutineContext.Key<MyProjectMarker>

    override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

    override val key: CoroutineContext.Key<*>
      get() = Key
  }

  private fun getDurationFromStart(
    finishTimestampNano: Long = System.nanoTime(),
    lastReportedEvent: LastHandledEvent?,
  ): Duration {
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

internal class WelcomeScreenPerformanceCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = WELCOME_SCREEN_GROUP
}

private val GROUP = EventLogGroup("reopen.project.startup.performance", 3)

private enum class UIResponseType {
  Splash,
  Frame,
}

private val UI_RESPONSE_TYPE = EventFields.Enum("type", UIResponseType::class.java)
private val FIRST_UI_SHOWN_EVENT: EventId2<Duration, UIResponseType> = GROUP.registerEvent("first.ui.shown", DURATION, UI_RESPONSE_TYPE)

private val PROJECT_ORDER_FIELD = EventFields.Int("project_order")
private val NUMBER_OF_PROJECTS_FIELD = EventFields.Int("number_of_projects")

private val PROJECTS_TYPE: EnumEventField<FUSProjectHotStartUpMeasurer.ProjectsType> =
  EventFields.Enum("projects_type", FUSProjectHotStartUpMeasurer.ProjectsType::class.java)
private val HAS_SETTINGS: BooleanEventField = EventFields.Boolean("has_settings")
private val VIOLATION: EnumEventField<FUSProjectHotStartUpMeasurer.Violation> =
  EventFields.Enum("violation", FUSProjectHotStartUpMeasurer.Violation::class.java)
private val FRAME_BECAME_VISIBLE_EVENT = GROUP.registerVarargEvent("frame.became.visible",
                                                                   DURATION, HAS_SETTINGS, PROJECTS_TYPE, VIOLATION)
private val MULTIPLE_PROJECT_FRAME_BECAME_VISIBLE_EVENT = GROUP.registerVarargEvent("multiple.project.frame.became.visible",
                                                                                    DURATION, HAS_SETTINGS, PROJECTS_TYPE, VIOLATION,
                                                                                    PROJECT_ORDER_FIELD, NUMBER_OF_PROJECTS_FIELD)

private val FRAME_BECAME_INTERACTIVE_EVENT = GROUP.registerEvent("frame.became.interactive", DURATION)
private val MULTIPLE_PROJECT_FRAME_BECAME_INTERACTIVE_EVENT = GROUP.registerEvent("multiple.project.frame.became.interactive",
                                                                                  DURATION, PROJECT_ORDER_FIELD, NUMBER_OF_PROJECTS_FIELD)

private enum class SourceOfSelectedEditor {
  TextEditor,
  UnknownEditor,
  FoundReadmeFile,
}

private val LOADED_CACHED_HIGHLIGHTING_MARKUP_FIELD = EventFields.Boolean("loaded_cached_markup")
private val LOADED_CACHED_CODE_FOLDING_MARKUP_FIELD = EventFields.Boolean("loaded_cached_code_folding_markup")
private val LOADED_CACHED_CODE_VISION_MARKUP_FIELD = EventFields.Boolean("loaded_cached_code_vision_markup")
private val LOADED_CACHED_DECLARATIVE_HINTS_MARKUP_FIELD = EventFields.Boolean("loaded_cached_declarative_hints_markup")
private val LOADED_CACHED_PARAMETER_HINTS_MARKUP_FIELD = EventFields.Boolean("loaded_cached_parameter_hints_markup")
private val LOADED_CACHED_DOC_RENDER_MARKUP_FIELD = EventFields.Boolean("loaded_cached_doc_render_markup")
private val SOURCE_OF_SELECTED_EDITOR_FIELD: EnumEventField<SourceOfSelectedEditor> =
  EventFields.Enum("source_of_selected_editor", SourceOfSelectedEditor::class.java)
private val NO_EDITORS_TO_OPEN_FIELD = EventFields.Boolean("no_editors_to_open")
private val CODE_LOADED_AND_VISIBLE_IN_EDITOR_EVENT = GROUP.registerVarargEvent("code.loaded.and.visible.in.editor", *createCodeLoadedEventFields(false))

private val MULTIPLE_PROJECT_CODE_LOADED_AND_VISIBLE_IN_EDITOR_EVENT = GROUP.registerVarargEvent("multiple.project.code.loaded.and.visible.in.editor",
                                                                                                 *createCodeLoadedEventFields(true))

private fun getField(type: MarkupType): EventField<Boolean> {
  return when (type) {
    MarkupType.HIGHLIGHTING -> LOADED_CACHED_HIGHLIGHTING_MARKUP_FIELD
    MarkupType.CODE_FOLDING -> LOADED_CACHED_CODE_FOLDING_MARKUP_FIELD
    MarkupType.CODE_VISION -> LOADED_CACHED_CODE_VISION_MARKUP_FIELD
    MarkupType.DECLARATIVE_HINTS -> LOADED_CACHED_DECLARATIVE_HINTS_MARKUP_FIELD
    MarkupType.PARAMETER_HINTS -> LOADED_CACHED_PARAMETER_HINTS_MARKUP_FIELD
    MarkupType.DOC_RENDER -> LOADED_CACHED_DOC_RENDER_MARKUP_FIELD
  }
}

private fun createCodeLoadedEventFields(forMultipleProjectsEvent: Boolean): Array<EventField<*>> {
  val fields = mutableListOf<EventField<*>>(DURATION, EventFields.FileType, HAS_SETTINGS, NO_EDITORS_TO_OPEN_FIELD, SOURCE_OF_SELECTED_EDITOR_FIELD)
  for (type in MarkupType.entries) {
    fields.add(getField(type))
  }
  if (forMultipleProjectsEvent) {
    fields.add(PROJECT_ORDER_FIELD)
    fields.add(NUMBER_OF_PROJECTS_FIELD)
  }
  return fields.toTypedArray()
}


internal class HotProjectReopenStartUpPerformanceCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}

@TestOnly
@Service(Service.Level.APP)
class FUSProjectHotStartUpMeasurerService {
  @TestOnly
  fun isHandlingFinished(): Boolean {
    return statsIsWritten
  }
}