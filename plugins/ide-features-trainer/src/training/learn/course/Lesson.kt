// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.course

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import training.dsl.TaskTestContext
import training.learn.CourseManager
import training.learn.lesson.LessonListener
import training.learn.lesson.LessonState
import training.learn.lesson.LessonStateManager
import training.statistic.LessonStartingWay
import training.util.LessonEndInfo
import training.util.filterUnseenLessons

abstract class Lesson(@NonNls val id: String, @Nls val name: String) {
  abstract val module: IftModule

  open val languageId: String? get() = module.primaryLanguage?.primaryLanguage

  open val lessonType: LessonType get() = module.moduleType

  open fun preferredLearnWindowAnchor(project: Project): ToolWindowAnchor = module.preferredLearnWindowAnchor(project)

  /**
   * Relative path to file in the learning project. Will be used existed or generated the new empty file.
   *
   * Also this non-null value will be used for scratch file name if this is a scratch lesson.
   */
  open val sampleFilePath: String? = null

  /** This method is called for all project-based lessons before the start of any project-based lesson */
  @RequiresBackgroundThread
  open fun prepare(project: Project) = Unit

  open val properties: LessonProperties = LessonProperties()

  /** Map: name -> url */
  open val helpLinks: Map<String, String> get() = emptyMap()

  /** IDs of TipAndTrick suggestions in that this lesson can be promoted */
  open val suitableTips: List<String> = emptyList()

  open val testScriptProperties: TaskTestContext.TestScriptProperties = TaskTestContext.TestScriptProperties()

  open fun onLessonEnd(project: Project, lessonEndInfo: LessonEndInfo) = Unit

  fun addLessonListener(lessonListener: LessonListener) {
    lessonListeners.add(lessonListener)
  }

  fun removeLessonListener(lessonListener: LessonListener) {
    lessonListeners.remove(lessonListener)
  }

  //-------------------------------------------------------------

  internal val passed: Boolean
    get() = LessonStateManager.getStateFromBase(id) == LessonState.PASSED

  internal val lessonListeners: MutableList<LessonListener> = mutableListOf()

  internal fun onStart(way: LessonStartingWay) {
    lessonListeners.forEach { it.lessonStarted(this, way) }
  }

  internal fun onStop(project: Project, lessonPassed: Boolean, currentTaskIndex: Int, currentVisualIndex: Int) {
    lessonListeners.forEach { it.lessonStopped(this) }
    onLessonEnd(project, LessonEndInfo(lessonPassed, currentTaskIndex, currentVisualIndex))
  }

  internal fun pass() {
    LessonStateManager.setPassed(this)
    lessonListeners.forEach { it.lessonPassed(this) }
  }

  internal fun isNewLesson(): Boolean {
    val availableSince = properties.availableSince ?: return false
    val lessonVersion = BuildNumber.fromString(availableSince) ?: return false

    val previousOpenedVersion = CourseManager.instance.previousOpenedVersion
    if (previousOpenedVersion  != null) {
      return previousOpenedVersion < lessonVersion
    } else {
      return filterUnseenLessons(module.lessons).contains(this)
    }
  }
}
