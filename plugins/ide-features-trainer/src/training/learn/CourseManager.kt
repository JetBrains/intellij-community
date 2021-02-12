// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn

import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import training.lang.LangManager
import training.lang.LangSupport
import training.learn.course.IftModule
import training.learn.course.LearningCourse
import training.learn.course.Lesson
import training.learn.lesson.LessonManager
import training.ui.LearnToolWindowFactory
import training.util.WeakReferenceDelegator
import training.util.courseCanBeUsed
import training.util.switchOnExperimentalLessons

class CourseManager internal constructor() : Disposable {
  val mapModuleVirtualFile: MutableMap<IftModule, VirtualFile> = ContainerUtil.createWeakMap()

  var unfoldModuleOnInit by WeakReferenceDelegator<IftModule>()

  private var allModules: List<IftModule>? = null

  private var currentConfiguration = switchOnExperimentalLessons

  val modules: List<IftModule>
    get() = LangManager.getInstance().getLangSupport()?.let { filterByLanguage(it) } ?: emptyList()

  val lessonsForModules: List<Lesson>
    get() = modules.map { it.lessons }.flatten()

  override fun dispose() {
  }

  init {
    COURSE_MODULES_EP.addChangeListener(Runnable {
      clearModules()
      for (toolWindow in LearnToolWindowFactory.learnWindowPerProject.values) {
        toolWindow.reinitViews()
      }
    }, this)
  }

  fun clearModules() {
    allModules = null
  }

  //TODO: remove this method or convert XmlModule to a Module
  fun registerVirtualFile(module: IftModule, virtualFile: VirtualFile) {
    mapModuleVirtualFile[module] = virtualFile
  }

  /**
   * @param projectWhereToOpen -- where to open projectWhereToOpen
   */
  fun openLesson(projectWhereToOpen: Project, lesson: Lesson?) {
    LessonManager.instance.stopLesson()
    if (lesson == null) return //todo: remove null lessons
    OpenLessonActivities.openLesson(projectWhereToOpen, lesson)
  }

  fun findLesson(lessonName: String): Lesson? {
    return modules
      .flatMap { it.lessons }
      .firstOrNull { it.name.equals(lessonName, ignoreCase = true) }
  }

  fun calcLessonsForLanguage(primaryLangSupport: LangSupport): Int {
    return ContainerUtil.concat(filterByLanguage(primaryLangSupport).map { m -> m.lessons }).size
  }

  fun calcPassedLessonsForLanguage(primaryLangSupport: LangSupport): Int {
    return filterByLanguage(primaryLangSupport)
      .flatMap { m -> m.lessons }
      .filter { it.passed }
      .size
  }

  private fun initAllModules(): List<IftModule> = COURSE_MODULES_EP.extensions
    .filter { courseCanBeUsed(it.language) }
    .map { it.instance.modules() }.flatten()

  private fun getAllModules(): List<IftModule> {
    if (currentConfiguration != switchOnExperimentalLessons) {
      allModules = null
      currentConfiguration = switchOnExperimentalLessons
    }
    return allModules ?: initAllModules().also { allModules = it }
  }

  private fun filterByLanguage(primaryLangSupport: LangSupport): List<IftModule> {
    return getAllModules().filter { it.primaryLanguage == primaryLangSupport }
  }

  companion object {
    val instance: CourseManager
      get() = ApplicationManager.getApplication().getService(CourseManager::class.java)
    val COURSE_MODULES_EP = ExtensionPointName<LanguageExtensionPoint<LearningCourse>>("training.ift.learning.course")
  }
}