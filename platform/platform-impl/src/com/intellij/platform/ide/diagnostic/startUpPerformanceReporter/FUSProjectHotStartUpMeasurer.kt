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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Internal
object FUSProjectHotStartUpMeasurer {

  enum class ProjectsType {
    Reopened, FromFilesToLoad, FromArgs, Unknown
  }

  enum class Violation {
    MightBeLightEditProject, MultipleProjects, NoProjectFound, WelcomeScreenShown, OpeningURI, ApplicationStarter, HasOpenedProject
  }

  /**
   * Might happen before [getStartUpContextElementIntoIdeStarter]
   */
  fun splashBecameVisible() {
    FUSStartupReopenProjectElement.splashBecameVisible()
  }

  fun getStartUpContextElementIntoIdeStarter(ideStarter: IdeStarter): CoroutineContext.Element? {
    if (ideStarter.isHeadless) {
      return null
    }
    if (ideStarter.javaClass !in listOf(IdeStarter::class.java, IdeStarter.StandaloneLightEditStarter::class.java)) {
      return null
    }
    FUSStartupReopenProjectElement.ideStarterStarted()
    return FUSStartupReopenProjectElement
  }

  suspend fun getStartUpContextElementToPass(): CoroutineContext.Element? {
    return coroutineContext[MyMarker]?.getStartUpContextElementToPass()
  }

  fun reportWelcomeScreenShown() {
    FUSStartupReopenProjectElement.reportWelcomeScreenShown()
  }

  suspend fun reportProjectType(projectsType: ProjectsType) {
    coroutineContext[MyMarker]?.reportProjectType(projectsType)
  }

  /**
   * Reports the existence of project settings to filter cases of importing which may need more resources.
   */
  suspend fun reportProjectPath(projectFile: Path) {
    val element = coroutineContext[MyMarker] ?: return
    val hasSettings = withContext(Dispatchers.IO) { ProjectUtilCore.isValidProjectPath(projectFile) }
    element.reportProjectSettings(hasSettings)
  }

  suspend fun resetProjectPath() {
    coroutineContext[MyMarker]?.resetProjectSettings()
  }

  suspend fun openingMultipleProjects() {
    coroutineContext[MyMarker]?.reportViolation(Violation.MultipleProjects)
  }

  suspend fun reportAlreadyOpenedProject() {
    coroutineContext[MyMarker]?.reportViolation(Violation.HasOpenedProject)
  }

  fun noProjectFound() {
    FUSStartupReopenProjectElement.reportViolation(Violation.NoProjectFound)
  }

  fun lightEditProjectFound() {
    FUSStartupReopenProjectElement.reportViolation(Violation.MightBeLightEditProject)
  }

  suspend fun reportUriOpening() {
    coroutineContext[MyMarker]?.reportViolation(Violation.OpeningURI)
  }

  fun reportStarterUsed() {
    FUSStartupReopenProjectElement.reportViolation(Violation.ApplicationStarter)
  }

  fun frameBecameVisible() {
    FUSStartupReopenProjectElement.reportFrameBecameVisible()
  }

  fun reportFrameBecameInteractive() {
    FUSStartupReopenProjectElement.reportFrameBecameInteractive()
  }

  fun markupRestored(file: VirtualFileWithId) {
    FUSStartupReopenProjectElement.reportMarkupRestored(file)
  }

  fun firstOpenedEditor(project: Project, file: VirtualFile, elementToPass: CoroutineContext.Element?) {
    if (elementToPass != FUSStartupReopenProjectElement) return
    service<MeasurerCoroutineService>().coroutineScope.launch {
      FUSStartupReopenProjectElement.reportFirstEditor(project, file, SourceOfSelectedEditor.TextEditor)
    }
  }

  suspend fun firstOpenedUnknownEditor(project: Project, file: VirtualFile) {
    coroutineContext[MyMarker]?.reportFirstEditor(project, file, SourceOfSelectedEditor.UnknownEditor)
  }

  suspend fun openedReadme(project: Project, readmeFile: VirtualFile) {
    coroutineContext[MyMarker]?.reportFirstEditor(project, readmeFile, SourceOfSelectedEditor.FoundReadmeFile)
  }

  fun reportNoMoreEditorsOnStartup(project: Project, startUpContextElementToPass: CoroutineContext.Element?) {
    if (startUpContextElementToPass == FUSStartupReopenProjectElement) {
      FUSStartupReopenProjectElement.reportNoMoreEditorsOnStartup(project)
    }
  }

  private object MyMarker : CoroutineContext.Key<FUSStartupReopenProjectElement>

  private object FUSStartupReopenProjectElement : CoroutineContext.Element {
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

    private val stageLock = Object()

    @Volatile
    private var stage: Stage = Stage.Initial

    private val markupResurrectedFileIds = IntOpenHashSet()

    override val key: CoroutineContext.Key<*>
      get() = MyMarker

    private fun <T> computeLocked(checkIsInitialized: Boolean = true, block: Stage.() -> T): T {
      synchronized(stageLock) {
        stage.apply {
          if (checkIsInitialized && (this is Stage.Initial || this is Stage.SplashScreenShownBeforeIdeStarter)) {
            stage = Stage.Stopped
            synchronized(markupResurrectedFileIds) { markupResurrectedFileIds.clear() }
          }
        }
        return stage.block()
      }
    }

    fun ideStarterStarted() {
      computeLocked(false) {
        when (this) {
          is Stage.Initial -> {
            stage = Stage.IdeStarterStarted()
          }
          is Stage.SplashScreenShownBeforeIdeStarter -> {
            stage = Stage.IdeStarterStarted(splashBecameVisibleTime = this.splashBecameVisibleTime)
          }
          else -> {
            stopReporting()
          }
        }
      }
    }

    fun splashBecameVisible() {
      val nanoTime = System.nanoTime()
      // This may happen before we know about particulars in com.intellij.idea.IdeStarter.startIDE,
      // where initialization of FUSStartupReopenProjectMarkerElement happens.
      computeLocked(false) {
        if (this is Stage.Initial) {
          stage = Stage.SplashScreenShownBeforeIdeStarter(nanoTime)
        }
        else if (this is Stage.IdeStarterStarted && splashBecameVisibleTime == null) {
          stage = this.copy(splashBecameVisibleTime = nanoTime)
        }
      }
    }

    fun reportViolation(violation: Violation) {
      computeLocked {
        if (this is Stage.IdeStarterStarted) {
          reportViolation(getDurationFromStart(), violation, splashBecameVisibleTime)
        }
      }
    }

    fun reportWelcomeScreenShown() {
      val welcomeScreenDuration = getDurationFromStart()
      computeLocked {
        if (this !is Stage.IdeStarterStarted) return@computeLocked
        if (splashBecameVisibleTime == null) {
          WELCOME_SCREEN_EVENT.log(DURATION.with(welcomeScreenDuration.getValueForFUS()), SPLASH_SCREEN_WAS_SHOWN.with(false))
        }
        else {
          WELCOME_SCREEN_EVENT.log(DURATION.with(welcomeScreenDuration.getValueForFUS()), SPLASH_SCREEN_WAS_SHOWN.with(true),
                                   SPLASH_SCREEN_VISIBLE_DURATION.with(getDurationFromStart(splashBecameVisibleTime).getValueForFUS()))
        }
        reportViolation(Violation.WelcomeScreenShown)
      }
    }

    fun reportProjectType(projectsType: ProjectsType) {
      computeLocked {
        if (this is Stage.IdeStarterStarted && projectType == ProjectsType.Unknown) {
          stage = this.copy(projectType = projectsType)
        }
      }
    }

    fun reportProjectSettings(exist: Boolean) {
      computeLocked {
        if (this is Stage.IdeStarterStarted && settingsExist == null) {
          stage = this.copy(settingsExist = exist)
        }
      }
    }

    fun resetProjectSettings() {
      computeLocked {
        if (this is Stage.IdeStarterStarted && settingsExist != null) {
          stage = this.copy(settingsExist = null)
        }
      }
    }

    fun reportFrameBecameVisible() {
      computeLocked {
        if (this !is Stage.IdeStarterStarted) {
          return@computeLocked
        }
        val duration = getDurationFromStart()

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
            stopReporting()
          }
          else {
            stage = Stage.FrameInteractive(settingsExist)
          }
        }
        else {
          stage = Stage.FrameVisible(prematureEditorData, settingsExist)
        }
      }
    }

    private fun reportViolation(
      duration: Duration,
      violation: Violation,
      splashBecameVisibleTime: Long?
    ) {
      reportFirstUiShownEvent(splashBecameVisibleTime, duration)
      FRAME_BECAME_VISIBLE_EVENT.log(DURATION.with(duration.getValueForFUS()), VIOLATION.with(violation))
      stopReporting()
    }

    private fun reportFirstUiShownEvent(splashBecameVisibleTime: Long?, duration: Duration) {
      splashBecameVisibleTime?.also {
        FIRST_UI_SHOWN_EVENT.log(getDurationFromStart(splashBecameVisibleTime).getValueForFUS(), UIResponseType.Splash)
      }.alsoIfNull { FIRST_UI_SHOWN_EVENT.log(duration.getValueForFUS(), UIResponseType.Frame) }
    }

    fun reportFrameBecameInteractive() {
      computeLocked {
        if (this is Stage.IdeStarterStarted && prematureFrameInteractive == null) {
          stage = this.copy(prematureFrameInteractive = PrematureFrameInteractiveData)
        }
        else if (this is Stage.FrameVisible) {
          val duration = getDurationFromStart()
          PrematureFrameInteractiveData.log(duration)
          if (prematureEditorData != null) {
            Stage.EditorStage(prematureEditorData, settingsExist).log(duration)
            stopReporting()
          }
          else {
            stage = Stage.FrameInteractive(settingsExist)
          }
        }
      }
    }

    private fun isMarkupLoaded(file: VirtualFile): Boolean {
      if (file !is VirtualFileWithId) return false
      return synchronized(markupResurrectedFileIds) { markupResurrectedFileIds.contains(file.id) }
    }

    suspend fun reportFirstEditor(project: Project, file: VirtualFile, sourceOfSelectedEditor: SourceOfSelectedEditor) {
      val durationMillis = System.nanoTime()

      withContext(Dispatchers.Default) {
        computeLocked {
          if (this is Stage.Stopped) return@computeLocked
          if (this is Stage.IdeStarterStarted && prematureEditorData != null) return@computeLocked
          if (this is Stage.FrameVisible && prematureEditorData != null) return@computeLocked

          val isMarkupLoaded = isMarkupLoaded(file)
          val fileType = ReadAction.nonBlocking<FileType> { return@nonBlocking file.fileType }.executeSynchronously()
          val editorStageData = PrematureEditorStageData.FirstEditor(project, sourceOfSelectedEditor, fileType, isMarkupLoaded)

          when (this) {
            is Stage.IdeStarterStarted -> {
              stage = this.copy(prematureEditorData = editorStageData)
            }
            is Stage.FrameVisible -> {
              stage = this.copy(prematureEditorData = editorStageData)
            }
            is Stage.FrameInteractive -> {
              stopReporting()
              Stage.EditorStage(editorStageData, settingsExist).log(getDurationFromStart(durationMillis))
            }
            else -> {} //ignore
          }
        }
      }
    }

    private fun stopReporting() {
      computeLocked {
        stage = Stage.Stopped
        synchronized(markupResurrectedFileIds) { markupResurrectedFileIds.clear() }
      }
    }

    fun getStartUpContextElementToPass(): CoroutineContext.Element? {
      return computeLocked { return@computeLocked if (this == Stage.Stopped) null else this@FUSStartupReopenProjectElement }
    }

    fun reportNoMoreEditorsOnStartup(project: Project) {
      val durationMillis = System.nanoTime()
      val noEditorStageData = PrematureEditorStageData.NoEditors(project)
      computeLocked {
        when (this) {
          is Stage.Stopped -> return@computeLocked
          is Stage.IdeStarterStarted -> {
            if (prematureEditorData != null) return@computeLocked
            stage = this.copy(prematureEditorData = noEditorStageData)
          }
          is Stage.FrameVisible -> {
            if (prematureEditorData != null) return@computeLocked
            stage = this.copy(prematureEditorData = noEditorStageData)
          }
          is Stage.FrameInteractive -> {
            Stage.EditorStage(noEditorStageData, settingsExist).log(getDurationFromStart(durationMillis))
            stopReporting()
          }
          else -> {} //ignore
        }
      }
    }

    fun reportMarkupRestored(file: VirtualFileWithId) {
      computeLocked {
        if (this == Stage.Stopped) {
          return@computeLocked
        }
        synchronized(markupResurrectedFileIds) { markupResurrectedFileIds.add(file.id) }
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