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
  private val markupResurrectedFileIds = IntOpenHashSet()
  private val stageLock = Object()
  @Volatile
  private var stage: Stage = Stage.Initial

  enum class ProjectsType {
    Reopened, FromFilesToLoad, FromArgs, Unknown
  }

  enum class Violation {
    MightBeLightEditProject, MultipleProjects, NoProjectFound, WelcomeScreenShown, OpeningURI, ApplicationStarter, HasOpenedProject
  }

  private suspend fun onProperContext(block: suspend () -> Unit) {
    if (currentCoroutineContext()[MyMarker] != null) {
      block()
    }
  }

  private fun computeLocked(block: Stage.() -> Stage) {
    synchronized(stageLock) {
      if (stage !== Stage.Stopped) {
        stage = stage.block()
      }
      if (stage === Stage.Stopped) {
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
    computeLocked {
      return@computeLocked when {
        this is Stage.Initial -> Stage.SplashScreenShownBeforeIdeStarter(nanoTime)
        this is Stage.IdeStarterStarted && splashBecameVisibleTime == null -> this.copy(splashBecameVisibleTime = nanoTime)
        else -> Stage.Stopped
      }
    }
  }

  fun getStartUpContextElementIntoIdeStarter(ideStarter: IdeStarter): CoroutineContext.Element? {
    if (ideStarter.isHeadless ||
        ideStarter.javaClass !in listOf(IdeStarter::class.java, IdeStarter.StandaloneLightEditStarter::class.java)) {
      computeLocked { Stage.Stopped }
      return null
    }
    computeLocked {
      return@computeLocked when (this) {
        is Stage.Initial -> Stage.IdeStarterStarted()
        is Stage.SplashScreenShownBeforeIdeStarter -> Stage.IdeStarterStarted(splashBecameVisibleTime = this.splashBecameVisibleTime)
        else -> Stage.Stopped
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
      if (this is Stage.IdeStarterStarted) {
        reportFirstUiShownEvent(splashBecameVisibleTime, duration)
        FRAME_BECAME_VISIBLE_EVENT.log(DURATION.with(duration.getValueForFUS()), VIOLATION.with(violation))
      }
      return@computeLocked Stage.Stopped
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
      if (this is Stage.IdeStarterStarted) {
        if (splashBecameVisibleTime == null) {
          WELCOME_SCREEN_EVENT.log(DURATION.with(welcomeScreenDuration.getValueForFUS()), SPLASH_SCREEN_WAS_SHOWN.with(false))
        }
        else {
          WELCOME_SCREEN_EVENT.log(DURATION.with(welcomeScreenDuration.getValueForFUS()), SPLASH_SCREEN_WAS_SHOWN.with(true),
                                   SPLASH_SCREEN_VISIBLE_DURATION.with(getDurationFromStart(splashBecameVisibleTime).getValueForFUS()))
        }
        reportViolation(Violation.WelcomeScreenShown)
      }
      return@computeLocked Stage.Stopped
    }
  }

  suspend fun reportProjectType(projectsType: ProjectsType) {
    onProperContext {
      computeLocked {
        return@computeLocked when {
          this is Stage.IdeStarterStarted && projectType == ProjectsType.Unknown -> this.copy(projectType = projectsType)
          else -> Stage.Stopped
        }
      }
    }
  }

  /**
   * Reports the existence of project settings to filter cases of importing which may need more resources.
   */
  suspend fun reportProjectPath(projectFile: Path) {
    onProperContext {
      val hasSettings = withContext(Dispatchers.IO) { ProjectUtilCore.isValidProjectPath(projectFile) }
      computeLocked {
        return@computeLocked when {
          this is Stage.IdeStarterStarted && settingsExist == null -> this.copy(settingsExist = hasSettings)
          else -> Stage.Stopped
        }
      }
    }
  }

  suspend fun resetProjectPath() {
    onProperContext {
      computeLocked {
        return@computeLocked when {
          this is Stage.IdeStarterStarted && settingsExist != null -> this.copy(settingsExist = null)
          else -> Stage.Stopped
        }
      }
    }
  }

  suspend fun openingMultipleProjects() {
    onProperContext { reportViolation(Violation.MultipleProjects) }
  }

  suspend fun reportAlreadyOpenedProject() {
    onProperContext { reportViolation(Violation.HasOpenedProject) }
  }

  fun noProjectFound() {
    reportViolation(Violation.NoProjectFound)
  }

  fun lightEditProjectFound() {
    reportViolation(Violation.MightBeLightEditProject)
  }

  suspend fun reportUriOpening() {
    onProperContext { reportViolation(Violation.OpeningURI) }
  }

  fun reportStarterUsed() {
    reportViolation(Violation.ApplicationStarter)
  }

  fun frameBecameVisible() {
    val duration = getDurationFromStart()
    computeLocked {
      if (this is Stage.IdeStarterStarted) {
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
            Stage.EditorStage(prematureEditorData, settingsExist).log(duration)
            return@computeLocked Stage.Stopped
          }
          else {
            return@computeLocked Stage.FrameInteractive(settingsExist)
          }
        }
        else {
          return@computeLocked Stage.FrameVisible(prematureEditorData, settingsExist)
        }
      }
      return@computeLocked Stage.Stopped
    }
  }

  fun reportFrameBecameInteractive() {
    val duration = getDurationFromStart()
    computeLocked {
      when {
        this is Stage.IdeStarterStarted && prematureFrameInteractive == null -> {
          return@computeLocked this.copy(prematureFrameInteractive = PrematureFrameInteractiveData)
        }
        this is Stage.FrameVisible && prematureEditorData == null -> {
          PrematureFrameInteractiveData.log(duration)
          return@computeLocked Stage.FrameInteractive(settingsExist)
        }
        this is Stage.FrameVisible && prematureEditorData != null -> {
          PrematureFrameInteractiveData.log(duration)
          Stage.EditorStage(prematureEditorData, settingsExist).log(duration)
          return@computeLocked Stage.Stopped
        }
        else -> {
          return@computeLocked Stage.Stopped
        }
      }
    }
  }

  fun markupRestored(file: VirtualFileWithId) {
    computeLocked {
      return@computeLocked when (this) {
        is Stage.Initial -> Stage.Stopped
        is Stage.SplashScreenShownBeforeIdeStarter -> Stage.Stopped
        else -> {
          synchronized(markupResurrectedFileIds) { markupResurrectedFileIds.add(file.id) }
          this
        }
      }
    }
  }

  private suspend fun reportFirstEditor(project: Project, file: VirtualFile, sourceOfSelectedEditor: SourceOfSelectedEditor) {
    val durationMillis = System.nanoTime()

    withContext(Dispatchers.Default) {
      computeLocked {
        fun getEditorStorageData(): PrematureEditorStageData {
          val isMarkupLoaded = (file is VirtualFileWithId) && synchronized(markupResurrectedFileIds) {
            markupResurrectedFileIds.contains(file.id)
          }
          val fileType = ReadAction.nonBlocking<FileType> { return@nonBlocking file.fileType }.executeSynchronously()
          return PrematureEditorStageData.FirstEditor(project, sourceOfSelectedEditor, fileType, isMarkupLoaded)
        }

        return@computeLocked when {
          this is Stage.IdeStarterStarted && prematureEditorData == null -> {
            this.copy(prematureEditorData = getEditorStorageData())
          }
          this is Stage.FrameVisible && prematureEditorData == null -> {
            this.copy(prematureEditorData = getEditorStorageData())
          }
          this is Stage.FrameInteractive -> {
            Stage.EditorStage(getEditorStorageData(), settingsExist).log(getDurationFromStart(durationMillis))
            Stage.Stopped
          }
          else -> Stage.Stopped
        }
      }
    }
  }

  fun firstOpenedEditor(project: Project, file: VirtualFile, elementToPass: CoroutineContext.Element?) {
    if (elementToPass != MyMarker) return
    service<MeasurerCoroutineService>().coroutineScope.launch(context = MyMarker) {
      onProperContext {
        reportFirstEditor(project, file, SourceOfSelectedEditor.TextEditor)
      }
    }
  }

  suspend fun firstOpenedUnknownEditor(project: Project, file: VirtualFile) {
    onProperContext { reportFirstEditor(project, file, SourceOfSelectedEditor.UnknownEditor) }
  }

  suspend fun openedReadme(project: Project, readmeFile: VirtualFile) {
    onProperContext { reportFirstEditor(project, readmeFile, SourceOfSelectedEditor.FoundReadmeFile) }
  }

  fun reportNoMoreEditorsOnStartup(project: Project, startUpContextElementToPass: CoroutineContext.Element?) {
    if (startUpContextElementToPass != MyMarker) {
      return
    }
    val durationMillis = System.nanoTime()
    val noEditorStageData = PrematureEditorStageData.NoEditors(project)
    computeLocked {
      return@computeLocked when {
        this is Stage.IdeStarterStarted && prematureEditorData == null -> {
          this.copy(prematureEditorData = noEditorStageData)
        }
        this is Stage.FrameVisible && prematureEditorData == null -> {
          this.copy(prematureEditorData = noEditorStageData)
        }
        this is Stage.FrameInteractive -> {
          Stage.EditorStage(noEditorStageData, settingsExist).log(getDurationFromStart(durationMillis))
          Stage.Stopped
        }
        else -> Stage.Stopped
      }
    }
  }

  private object MyMarker : CoroutineContext.Key<MyMarker>, CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
      get() = this
  }

  private sealed interface Stage {
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

  private data object PrematureFrameInteractiveData {
    fun log(duration: Duration) {
      FRAME_BECAME_INTERACTIVE_EVENT.log(duration.getValueForFUS())
    }
  }

  private sealed interface PrematureEditorStageData {
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