// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn

import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.KeyedLazyInstanceEP
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import training.lang.LangManager
import training.lang.LangSupport
import training.learn.course.IftModule
import training.learn.course.LearningCourse
import training.learn.course.LearningCourseBase
import training.learn.course.Lesson
import training.learn.lesson.LessonManager
import training.ui.LearnToolWindowFactory
import training.util.LEARNING_PANEL_OPENED_IN
import training.util.WeakReferenceDelegator
import training.util.courseCanBeUsed
import training.util.switchOnExperimentalLessons

@Service(Service.Level.APP)
class CourseManager internal constructor() : Disposable {

  val previousOpenedVersion: BuildNumber?

  init {
    val strBuild = PropertiesComponent.getInstance().getValue(LEARNING_PANEL_OPENED_IN)
    previousOpenedVersion = if (strBuild == null) {
      null
    }
    else {
      val parseVersion = BuildNumber.fromString(strBuild)
      if (parseVersion == null) {
        thisLogger().error("Cannot parse previous version $strBuild")
      }
      parseVersion
    }
  }

  val mapModuleVirtualFile: MutableMap<IftModule, VirtualFile> = ContainerUtil.createWeakMap()

  var unfoldModuleOnInit by WeakReferenceDelegator<IftModule>()

  private val languageCourses: MultiMap<LangSupport, IftModule> = MultiMap.create()
  private val commonCourses: MultiMap<String, IftModule> = MultiMap.create()

  private var currentConfiguration = switchOnExperimentalLessons

  val modules: Collection<IftModule>
    get() {
      prepareLangModules()
      return LangManager.getInstance().getLangSupport()?.let { languageCourses[it] } ?: emptyList()
    }

  val lessonsForModules: List<Lesson>
    get() = modules.map { it.lessons }.flatten()

  val newLessons: List<Lesson>
    get() {
      val previousVersion = previousOpenedVersion ?: return lessonsForModules.filter { it.properties.availableSince != null }
      return lessonsForModules.filter {
        if (it.passed) return@filter false // It is strange situation actually
        val availableSince = it.properties.availableSince ?: return@filter false
        val lessonVersion = BuildNumber.fromString(availableSince)
        if (lessonVersion == null) {
          thisLogger().error("Invalid lesson version: $availableSince")
          return@filter false
        }
        lessonVersion > previousVersion
      }
    }

  override fun dispose() {
  }

  init {
    for (ep in listOf(COMMON_COURSE_MODULES_EP, COURSE_MODULES_EP)) {
      ep.addChangeListener(Runnable {
        clearModules()
        for (toolWindow in LearnToolWindowFactory.learnWindowPerProject.values) {
          toolWindow.reinitViews()
        }
      }, this)
    }
  }

  fun clearModules() {
    languageCourses.clear()
    commonCourses.clear()
  }

  //TODO: remove this method or convert XmlModule to a Module
  fun registerVirtualFile(module: IftModule, virtualFile: VirtualFile) {
    mapModuleVirtualFile[module] = virtualFile
  }

  /**
   * @param projectWhereToOpen -- where to open projectWhereToOpen
   * @param forceStartLesson -- force start lesson without check for passed status (passed lessons will be opened as completed text)
   */
  fun openLesson(projectWhereToOpen: Project, lesson: Lesson?, forceStartLesson: Boolean = false) {
    LessonManager.instance.stopLesson()
    if (lesson == null) return //todo: remove null lessons
    OpenLessonActivities.openLesson(projectWhereToOpen, lesson, forceStartLesson)
  }

  fun findLessonById(lessonId: String): Lesson? {
    return lessonsForModules.firstOrNull { it.id == lessonId }
  }

  fun findCommonModules(commonCourseId: String): Collection<IftModule> {
    if (commonCourses.isEmpty) reloadCommonModules()
    return commonCourses[commonCourseId]
  }

  private fun reloadLangModules() {
    val extensions = COURSE_MODULES_EP.extensions.filter { courseCanBeUsed(it.language) }
    for (e in extensions) {
      val langSupport = LangManager.getInstance().getLangSupportById(e.language)
      if (langSupport != null) {
        languageCourses.putValues(langSupport, e.instance.modules())
      }
    }
  }

  private fun reloadCommonModules() {
    val commonCoursesExtensions = COMMON_COURSE_MODULES_EP.extensions
    for (e in commonCoursesExtensions) {
      if (commonCourses[e.key].isEmpty()) {
        commonCourses.put(e.key, e.instance.modules())
      }
    }
  }

  private fun prepareLangModules() {
    if (currentConfiguration != switchOnExperimentalLessons) {
      languageCourses.clear()
      currentConfiguration = switchOnExperimentalLessons
    }
    if (languageCourses.isEmpty) {
      reloadLangModules()
    }
  }

  companion object {
    val instance: CourseManager
      get() = ApplicationManager.getApplication().getService(CourseManager::class.java)
    val COURSE_MODULES_EP = ExtensionPointName<LanguageExtensionPoint<LearningCourseBase>>("training.ift.learning.course")
    val COMMON_COURSE_MODULES_EP = ExtensionPointName<KeyedLazyInstanceEP<LearningCourse>>("training.ift.learning.commonCourse")
  }
}