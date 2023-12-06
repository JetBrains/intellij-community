// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.course

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.components.panels.NonOpaquePanel
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import training.dsl.LearningBalloonConfig
import training.dsl.LessonContext
import training.dsl.waitSmartModeStep
import training.learn.LearnBundle
import training.ui.LearningUiHighlightingManager

abstract class KLesson(@NonNls id: String, @Nls name: String) : Lesson(id, name) {
  protected abstract val lessonContent: LessonContext.() -> Unit

  override lateinit var module: IftModule
    internal set

  open val fullLessonContent: LessonContext.() -> Unit get() = {
    showIndexingTask()
    lessonContent()
  }

  private fun LessonContext.showIndexingTask() {
    if (properties.canStartInDumbMode) return

    task {
      if (!isDumb(project)) return@task
      triggerAndBorderHighlight().component { progress: NonOpaquePanel ->
        progress.javaClass.name.contains("InlineProgressPanel")
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

  private fun isDumb(project: Project) = DumbService.getInstance(project).isDumb

  open fun beforeCaretApplied() { }
}
