// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.course

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import training.dsl.TaskTestContext
import training.learn.lesson.LessonListener
import training.learn.lesson.LessonState
import training.learn.lesson.LessonStateManager
import training.util.findLanguageByID

abstract class Lesson(@NonNls val id: String, @Nls val name: String) {
  abstract val module: IftModule

  /** This name will be used for generated file with lesson sample */
  open val fileName: String
    get() = module.sanitizedName + "." + findLanguageByID(languageId)!!.associatedFileType!!.defaultExtension

  open val languageId: String get() = module.primaryLanguage.primaryLanguage

  open val lessonType: LessonType get() = module.moduleType

  /** Relative path to existed file in the learning project */
  open val existedFile: String? = null

  /** This method is called for all project-based lessons before the start of any project-based lesson */
  @RequiresBackgroundThread
  open fun cleanup(project: Project) = Unit

  open val properties: LessonProperties = LessonProperties()

  /** Map: name -> url */
  open val helpLinks: Map<String, String> get() = emptyMap()

  open val testScriptProperties : TaskTestContext.TestScriptProperties = TaskTestContext.TestScriptProperties()

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

  internal fun onStart() {
    lessonListeners.forEach { it.lessonStarted(this) }
  }

  internal fun onStop() {
    lessonListeners.forEach { it.lessonStopped(this) }
  }

  internal fun pass() {
    LessonStateManager.setPassed(this)
    lessonListeners.forEach { it.lessonPassed(this) }
  }
}
