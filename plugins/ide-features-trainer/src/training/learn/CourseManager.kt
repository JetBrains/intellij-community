// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn

import com.intellij.ide.DataManager
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.containers.ContainerUtil
import training.actions.OpenLessonAction
import training.lang.LangManager
import training.lang.LangSupport
import training.learn.interfaces.Lesson
import training.learn.interfaces.Module
import training.learn.lesson.LessonManager
import training.ui.LearnToolWindowFactory
import training.util.switchOnExperimentalLessons

class CourseManager internal constructor() : Disposable {
  val mapModuleVirtualFile: MutableMap<Module, VirtualFile> = ContainerUtil.createWeakMap()

  private var allModules: List<Module>? = null

  private var currentConfiguration = switchOnExperimentalLessons

  val modules: List<Module>
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
  fun registerVirtualFile(module: Module, virtualFile: VirtualFile) {
    mapModuleVirtualFile[module] = virtualFile
  }

  /**
   * @param projectWhereToOpen -- where to open projectWhereToOpen
   */
  @Synchronized
  fun openLesson(projectWhereToOpen: Project, lesson: Lesson?) {
    LessonManager.instance.stopLesson()
    if (lesson == null) return //todo: remove null lessons
    val focusOwner = IdeFocusManager.getInstance(projectWhereToOpen).focusOwner
    val parent = DataManager.getInstance().getDataContext(focusOwner)
    val data = mutableMapOf<String, Any?>()
    val openLessonAction = OpenLessonAction(lesson)
    data[OpenLessonAction.PROJECT_WHERE_TO_OPEN_DATA_KEY.name] = projectWhereToOpen
    val context = SimpleDataContext.getSimpleContext(data, parent)
    val event = AnActionEvent.createFromAnAction(openLessonAction, null, "", context)
    ActionUtil.performActionDumbAware(openLessonAction, event)
  }

  fun findLesson(lessonName: String): Lesson? {
    return modules
      .flatMap { it.lessons }
      .firstOrNull { it.name.equals(lessonName, ignoreCase = true) }
  }

  fun getNextNonPassedLesson(currentLesson: Lesson?): Lesson? {
    val lessons = lessonsForModules
    val list = if (currentLesson != null) {
      val index = lessons.indexOf(currentLesson)
      if (index < 0) return null
      lessons.subList(index + 1, lessons.size) + lessons.subList(0, index + 1)
    }
    else {
      lessons
    }
    return list.find { !it.passed }
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

  private fun initAllModules(): List<Module> = COURSE_MODULES_EP.extensions.map { it.instance.modules() }.flatten()

  private fun getAllModules(): List<Module> {
    if (currentConfiguration != switchOnExperimentalLessons) {
      allModules = null
      currentConfiguration = switchOnExperimentalLessons
    }
    return allModules ?: initAllModules().also { allModules = it }
  }

  private fun filterByLanguage(primaryLangSupport: LangSupport): List<Module> {
    return getAllModules().filter { it.primaryLanguage == primaryLangSupport }
  }

  fun getModulesByLanguage(primaryLangSupport: LangSupport): List<Module> = filterByLanguage(primaryLangSupport)

  companion object {
    val instance: CourseManager
      get() = ApplicationManager.getApplication().getService(CourseManager::class.java)
    val COURSE_MODULES_EP = ExtensionPointName<LanguageExtensionPoint<LearningCourse>>("training.ift.learning.course")
  }

}