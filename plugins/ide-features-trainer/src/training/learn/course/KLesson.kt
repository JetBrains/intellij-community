// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.course

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import training.dsl.LearningBalloonConfig
import training.dsl.LessonContext
import training.dsl.waitSmartModeStep
import training.learn.LearnBundle
import training.ui.LearningUiHighlightingManager
import training.util.LearningLessonsAutoExecutor
import java.awt.Component

abstract class KLesson(@NonNls id: String, @Nls name: String) : Lesson(id, name) {
  protected abstract val lessonContent: LessonContext.() -> Unit

  override lateinit var module: IftModule
    internal set

  open val fullLessonContent: LessonContext.() -> Unit get() = {
    showIndexingTask()
    showBackgroundTasksTask()
    lessonContent()
  }

  private fun LessonContext.showIndexingTask() {
    if (properties.canStartInDumbMode) return

    task {
      if (!isDumb(project)) return@task
      triggerAndBorderHighlight().component { progress: NonOpaquePanel ->
        progress.javaClass.name.contains(PROGRESS_PANEL_CLASS_NAME)
      }
    }

    task {
      if (!isDumb(project)) return@task
      showWarning(LearnBundle.message("indexing.message")) {
        isDumb(project)
      }
      text(LearnBundle.message("indexing.message"), LearningBalloonConfig(Balloon.Position.above, 0, duplicateMessage = false))
      waitSmartModeStep()
    }

    prepareRuntimeTask {
      LearningUiHighlightingManager.clearHighlights()
    }
  }

  private fun LessonContext.showBackgroundTasksTask() {
    if (module.primaryLanguage?.waitForBackgroundTasksBeforeLesson != true) return

    task {
      runtimeText {
        if (!hasBackgroundTasks(project)) return@runtimeText null
        removeAfterDone = true
        LearnBundle.message("background.tasks.message")
      }
      // Some background tasks start with a small delay after the lesson preparation.
      // So the task passes only after several checks in a row find no background tasks.
      var checksWithoutTasks = 0
      var highlightedPanel: Component? = null
      timerCheck(BACKGROUND_TASKS_CHECK_DELAY_MS) {
        if (hasBackgroundTasks(project)) {
          checksWithoutTasks = 0
          highlightedPanel = highlightProgressPanel(project, highlightedPanel)
        }
        else {
          checksWithoutTasks++
          if (highlightedPanel != null) {
            highlightedPanel = null
            LearningUiHighlightingManager.clearHighlights()
          }
        }
        checksWithoutTasks >= REQUIRED_CHECKS_WITHOUT_TASKS
      }
    }
  }

  private fun hasBackgroundTasks(project: Project): Boolean {
    val statusBar = WindowManager.getInstance().getStatusBar(project) as? StatusBarEx ?: return false
    return statusBar.backgroundProcessModels.any { !LearningLessonsAutoExecutor.isAutoExecutorTask(it.first) }
  }

  /** Highlights the progress panel in the status bar. Returns the highlighted panel, or `null` if the panel is not shown. */
  private fun highlightProgressPanel(project: Project, highlightedPanel: Component?): Component? {
    val statusBarComponent = WindowManager.getInstance().getStatusBar(project)?.component ?: return null
    val panel = UIUtil.uiTraverser(statusBarComponent)
                  .filter(NonOpaquePanel::class.java)
                  .find { it.isShowing && it.javaClass.name.contains(PROGRESS_PANEL_CLASS_NAME) }
                ?: return null
    if (panel != highlightedPanel || !LearningUiHighlightingManager.highlightingComponents.contains(panel)) {
      LearningUiHighlightingManager.highlightComponent(panel, LearningUiHighlightingManager.HighlightingOptions(highlightInside = false))
    }
    return panel
  }

  private fun isDumb(project: Project) = DumbService.getInstance(project).isDumb

  open fun beforeCaretApplied(editor: Editor) { }

  private companion object {
    const val BACKGROUND_TASKS_CHECK_DELAY_MS = 300
    const val REQUIRED_CHECKS_WITHOUT_TASKS = 3
    const val PROGRESS_PANEL_CLASS_NAME = "InlineProgressPanel"
  }
}
