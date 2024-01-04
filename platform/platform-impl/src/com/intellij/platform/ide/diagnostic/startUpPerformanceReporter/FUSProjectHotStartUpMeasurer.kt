// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.diagnostic.startUpPerformanceReporter

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.idea.IdeStarter
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.alsoIfNull
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Internal
object FUSProjectHotStartUpMeasurer {
  private val stageHandler: StageHandler = StageHandler()
  private val stageLock = Object()

  @Volatile
  private var stage: StageHandler.Stage = StageHandler.Stage.Initial
  private val markupResurrectedFileIds = IntOpenHashSet()

  enum class ProjectsType {
    Reopened, FromFilesToLoad, FromArgs, Unknown
  }

  enum class Violation {
    MightBeLightEditProject, MultipleProjects, NoProjectFound, WelcomeScreenShown, OpeningURI, ApplicationStarter, HasOpenedProject
  }

  private fun onElement(block: StageHandler.() -> Unit) {
    stageHandler.block()
  }

  private suspend fun onElementSuspended(block: suspend StageHandler.() -> Unit) {
    if (currentCoroutineContext()[MyMarker] != null) {
      stageHandler.block()
    }
  }

  private fun computeLocked(checkIsInitialized: Boolean = true, block: StageHandler.Stage.() -> StageHandler.Stage) {
    synchronized(stageLock) {
      stage.apply {
        if (checkIsInitialized && (this is StageHandler.Stage.Initial || this is StageHandler.Stage.SplashScreenShownBeforeIdeStarter)) {
          stage = StageHandler.Stage.Stopped
        }
      }
      if (stage !== StageHandler.Stage.Stopped) {
        stage = stage.block()
      }
      if (stage === StageHandler.Stage.Stopped) {
        synchronized(markupResurrectedFileIds) { markupResurrectedFileIds.clear() }
      }
    }
  }

  /**
   * Might happen before [getStartUpContextElementIntoIdeStarter]
   */
  fun splashBecameVisible() {
    val nanoTime = System.nanoTime()
    // This may happen before we know about particulars in com.intellij.idea.IdeStarter.startIDE,
    // where initialization of FUSStartupReopenProjectMarkerElement happens.
    computeLocked(false) {
      return@computeLocked when {
        this is StageHandler.Stage.Initial -> StageHandler.Stage.SplashScreenShownBeforeIdeStarter(nanoTime)
        this is StageHandler.Stage.IdeStarterStarted && splashBecameVisibleTime == null -> this.copy(splashBecameVisibleTime = nanoTime)
        else -> StageHandler.Stage.Stopped
      }
    }
  }

  fun getStartUpContextElementIntoIdeStarter(ideStarter: IdeStarter): CoroutineContext.Element? {
    if (ideStarter.isHeadless) { //todo[lene] rewrite to clean stage
      return null
    }
    if (ideStarter.javaClass !in listOf(IdeStarter::class.java, IdeStarter.StandaloneLightEditStarter::class.java)) {
      return null
    }
    computeLocked(false) {
      return@computeLocked when (this) {
        is StageHandler.Stage.Initial -> StageHandler.Stage.IdeStarterStarted()
        is StageHandler.Stage.SplashScreenShownBeforeIdeStarter -> StageHandler.Stage.IdeStarterStarted(
          splashBecameVisibleTime = this.splashBecameVisibleTime)
        else -> StageHandler.Stage.Stopped
      }
    }
    return MyMarker
  }

  suspend fun getStartUpContextElementToPass(): CoroutineContext.Element? {
    return if (currentCoroutineContext()[MyMarker] == null) null else MyMarker
  }

  private fun reportViolation(violation: Violation) {
    val duration = getDurationFromStart()
    computeLocked {
      if (this is StageHandler.Stage.IdeStarterStarted) {
        reportFirstUiShownEvent(splashBecameVisibleTime, duration)
        FRAME_BECAME_VISIBLE_EVENT.log(DURATION.with(duration.getValueForFUS()), VIOLATION.with(violation))
      }
      return@computeLocked StageHandler.Stage.Stopped
    }
  }

  private fun reportFirstUiShownEvent(splashBecameVisibleTime: Long?, duration: Duration) {
    splashBecameVisibleTime?.also {
      FIRST_UI_SHOWN_EVENT.log(getDurationFromStart(splashBecameVisibleTime).getValueForFUS(), UIResponseType.Splash)
    }.alsoIfNull { FIRST_UI_SHOWN_EVENT.log(duration.getValueForFUS(), UIResponseType.Frame) }
  }

  fun reportWelcomeScreenShown() {
    val welcomeScreenDuration = getDurationFromStart()
    computeLocked {
      if (this is StageHandler.Stage.IdeStarterStarted) {
        if (splashBecameVisibleTime == null) {
          WELCOME_SCREEN_EVENT.log(DURATION.with(welcomeScreenDuration.getValueForFUS()), SPLASH_SCREEN_WAS_SHOWN.with(false))
        }
        else {
          WELCOME_SCREEN_EVENT.log(DURATION.with(welcomeScreenDuration.getValueForFUS()), SPLASH_SCREEN_WAS_SHOWN.with(true),
                                   SPLASH_SCREEN_VISIBLE_DURATION.with(getDurationFromStart(splashBecameVisibleTime).getValueForFUS()))
        }
        reportViolation(Violation.WelcomeScreenShown)
      }
      return@computeLocked StageHandler.Stage.Stopped
    }
  }

  suspend fun reportProjectType(projectsType: ProjectsType) {
    onElementSuspended {
      computeLocked {
        return@computeLocked if (this is StageHandler.Stage.IdeStarterStarted && projectType == ProjectsType.Unknown) {
          this.copy(projectType = projectsType)
        }
        else {
          StageHandler.Stage.Stopped
        }
      }
    }
  }

  /**
   * Reports the existence of project settings to filter cases of importing which may need more resources.
   */
  suspend fun reportProjectPath(projectFile: Path) {
    onElementSuspended {
      val hasSettings = withContext(Dispatchers.IO) { ProjectUtilCore.isValidProjectPath(projectFile) }
      computeLocked {
        return@computeLocked if (this is StageHandler.Stage.IdeStarterStarted && settingsExist == null) {
          this.copy(settingsExist = hasSettings)
        }
        else {
          StageHandler.Stage.Stopped
        }
      }
    }
  }

  suspend fun resetProjectPath() {
    onElementSuspended {
      computeLocked {
        return@computeLocked if (this is StageHandler.Stage.IdeStarterStarted && settingsExist != null) {
          this.copy(settingsExist = null)
        }
        else {
          StageHandler.Stage.Stopped
        }
      }
    }
  }

  suspend fun openingMultipleProjects() {
    onElementSuspended { reportViolation(Violation.MultipleProjects) }
  }

  suspend fun reportAlreadyOpenedProject() {
    onElementSuspended { reportViolation(Violation.HasOpenedProject) }
  }

  fun noProjectFound() {
    onElement { reportViolation(Violation.NoProjectFound) }
  }

  fun lightEditProjectFound() {
    onElement { reportViolation(Violation.MightBeLightEditProject) }
  }

  suspend fun reportUriOpening() {
    onElementSuspended { reportViolation(Violation.OpeningURI) }
  }

  fun reportStarterUsed() {
    onElement { reportViolation(Violation.ApplicationStarter) }
  }

  fun frameBecameVisible() {
    val duration = getDurationFromStart()
    computeLocked {
      if (this is StageHandler.Stage.IdeStarterStarted) {
        reportFirstUiShownEvent(splashBecameVisibleTime, duration)

        if (settingsExist == null) {
          FRAME_BECAME_VISIBLE_EVENT.log(DURATION.with(duration.getValueForFUS()), PROJECTS_TYPE.with(projectType))
        }
        else {
          FRAME_BECAME_VISIBLE_EVENT.log(DURATION.with(duration.getValueForFUS()), PROJECTS_TYPE.with(projectType),
                                         HAS_SETTINGS.with(settingsExist))
        }

        if (prematureFrameInteractive != null) {
          prematureFrameInteractive.log(duration)
          if (prematureEditorData != null) {
            StageHandler.Stage.EditorStage(prematureEditorData, settingsExist).log(duration)
            return@computeLocked StageHandler.Stage.Stopped
          }
          else {
            return@computeLocked StageHandler.Stage.FrameInteractive(settingsExist)
          }
        }
        else {
          return@computeLocked StageHandler.Stage.FrameVisible(prematureEditorData, settingsExist)
        }
      }
      return@computeLocked StageHandler.Stage.Stopped
    }
  }

  fun reportFrameBecameInteractive() {
    val duration = getDurationFromStart()
    computeLocked {
      if (this is StageHandler.Stage.IdeStarterStarted && prematureFrameInteractive == null) {
        return@computeLocked this.copy(prematureFrameInteractive = StageHandler.PrematureFrameInteractiveData)
      }
      else if (this is StageHandler.Stage.FrameVisible) {
        StageHandler.PrematureFrameInteractiveData.log(duration)
        if (prematureEditorData != null) {
          StageHandler.Stage.EditorStage(prematureEditorData, settingsExist).log(duration)
          return@computeLocked StageHandler.Stage.Stopped
        }
        else {
          return@computeLocked StageHandler.Stage.FrameInteractive(settingsExist)
        }
      }
      else {
        return@computeLocked StageHandler.Stage.Stopped
      }
    }
  }

  fun markupRestored(file: VirtualFileWithId) {
    computeLocked {
      synchronized(markupResurrectedFileIds) { markupResurrectedFileIds.add(file.id) }
      return@computeLocked this
    }
  }

  private suspend fun reportFirstEditor(project: Project, file: VirtualFile, sourceOfSelectedEditor: SourceOfSelectedEditor) {
    val durationMillis = System.nanoTime()

    withContext(Dispatchers.Default) {
      computeLocked {
        if (this is StageHandler.Stage.IdeStarterStarted && prematureEditorData != null) return@computeLocked StageHandler.Stage.Stopped
        if (this is StageHandler.Stage.FrameVisible && prematureEditorData != null) return@computeLocked StageHandler.Stage.Stopped

        val isMarkupLoaded = (file is VirtualFileWithId) && synchronized(markupResurrectedFileIds) {
          markupResurrectedFileIds.contains(file.id)
        }
        val fileType = ReadAction.nonBlocking<FileType> { return@nonBlocking file.fileType }.executeSynchronously()
        val editorStageData = StageHandler.PrematureEditorStageData.FirstEditor(project, sourceOfSelectedEditor, fileType, isMarkupLoaded)

        return@computeLocked when (this) {
          is StageHandler.Stage.IdeStarterStarted -> {
            this.copy(prematureEditorData = editorStageData)
          }
          is StageHandler.Stage.FrameVisible -> {
            this.copy(prematureEditorData = editorStageData)
          }
          is StageHandler.Stage.FrameInteractive -> {
            StageHandler.Stage.EditorStage(editorStageData, settingsExist).log(getDurationFromStart(durationMillis))
            StageHandler.Stage.Stopped
          }
          else -> StageHandler.Stage.Stopped
        }
      }
    }
  }

  fun firstOpenedEditor(project: Project, file: VirtualFile, elementToPass: CoroutineContext.Element?) {
    if (elementToPass != MyMarker) return
    service<MeasurerCoroutineService>().coroutineScope.launch(context = MyMarker) {
      onElementSuspended {
        reportFirstEditor(project, file, SourceOfSelectedEditor.TextEditor)
      }
    }
  }

  suspend fun firstOpenedUnknownEditor(project: Project, file: VirtualFile) {
    onElementSuspended { reportFirstEditor(project, file, SourceOfSelectedEditor.UnknownEditor) }
  }

  suspend fun openedReadme(project: Project, readmeFile: VirtualFile) {
    onElementSuspended { reportFirstEditor(project, readmeFile, SourceOfSelectedEditor.FoundReadmeFile) }
  }

  fun reportNoMoreEditorsOnStartup(project: Project, startUpContextElementToPass: CoroutineContext.Element?) {
    if (startUpContextElementToPass != MyMarker) {
      return
    }
    val durationMillis = System.nanoTime()
    val noEditorStageData = StageHandler.PrematureEditorStageData.NoEditors(project)
    computeLocked {
      return@computeLocked when (this) {
        is StageHandler.Stage.Stopped -> StageHandler.Stage.Stopped
        is StageHandler.Stage.IdeStarterStarted -> {
          if (prematureEditorData != null) StageHandler.Stage.Stopped else this.copy(prematureEditorData = noEditorStageData)
        }
        is StageHandler.Stage.FrameVisible -> {
          if (prematureEditorData != null) StageHandler.Stage.Stopped else this.copy(prematureEditorData = noEditorStageData)
        }
        is StageHandler.Stage.FrameInteractive -> {
          StageHandler.Stage.EditorStage(noEditorStageData, settingsExist).log(getDurationFromStart(durationMillis))
          StageHandler.Stage.Stopped
        }
        else -> StageHandler.Stage.Stopped
      }
    }
  }

  private object MyMarker : CoroutineContext.Key<MyMarker>, CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
      get() = this
  }

  private class StageHandler {
    sealed interface Stage {
      data object Initial : Stage
      data class SplashScreenShownBeforeIdeStarter(val splashBecameVisibleTime: Long) : Stage

      data class IdeStarterStarted(
        val splashBecameVisibleTime: Long? = null,
        val projectType: ProjectsType = ProjectsType.Unknown,
        val settingsExist: Boolean? = null,
        val prematureFrameInteractive: PrematureFrameInteractiveData? = null,
        val prematureEditorData: PrematureEditorStageData? = null
      ) : Stage

      data class FrameVisible(val prematureEditorData: PrematureEditorStageData? = null, val settingsExist: Boolean?) : Stage
      data class FrameInteractive(val settingsExist: Boolean?) : Stage

      data class EditorStage(val data: PrematureEditorStageData, val settingsExist: Boolean?) : Stage {
        fun log(duration: Duration) {
          val eventData = data.getEventData().add(DURATION.with(duration.getValueForFUS())).let { pairs ->
            settingsExist?.let { pairs.add(HAS_SETTINGS.with(settingsExist)) } ?: pairs
          }
          CODE_LOADED_AND_VISIBLE_IN_EDITOR_EVENT.log(data.project, eventData)
        }
      }

      data object Stopped : Stage
    }

    data object PrematureFrameInteractiveData {
      fun log(duration: Duration) {
        FRAME_BECAME_INTERACTIVE_EVENT.log(duration.getValueForFUS())
      }
    }

    sealed interface PrematureEditorStageData {
      val project: Project
      fun getEventData(): PersistentList<EventPair<*>>

      data class FirstEditor(
        override val project: Project,
        val sourceOfSelectedEditor: SourceOfSelectedEditor,
        val fileType: FileType,
        val isMarkupLoaded: Boolean
      ) : PrematureEditorStageData {
        override fun getEventData(): PersistentList<EventPair<*>> {
          return persistentListOf(
            SOURCE_OF_SELECTED_EDITOR_FIELD.with(sourceOfSelectedEditor),
            NO_EDITORS_TO_OPEN_FIELD.with(false),
            EventFields.FileType.with(fileType),
            LOADED_CACHED_MARKUP_FIELD.with(isMarkupLoaded)
          )
        }
      }

      data class NoEditors(override val project: Project) : PrematureEditorStageData {
        override fun getEventData(): PersistentList<EventPair<*>> {
          return persistentListOf(NO_EDITORS_TO_OPEN_FIELD.with(true))
        }
      }
    }

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