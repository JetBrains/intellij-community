// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn

import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.KeyedLazyInstanceEP
import com.intellij.util.containers.MultiMap
import training.lang.LangManager
import training.lang.LangSupport
import training.learn.course.IftModule
import training.learn.course.LearningCourse
import training.learn.course.LearningCourseBase
import training.learn.course.Lesson
import training.learn.lesson.LessonManager
import training.statistic.LessonStartingWay
import training.util.*

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

  var unfoldModuleOnInit by WeakReferenceDelegator<IftModule>()

  /**
   * [isExternal] equals true if [module] comes from the third party plugin
   */
  private class ModuleInfo(val module: IftModule, val isExternal: Boolean)

  private val languageCourses: MultiMap<LangSupport, ModuleInfo> = MultiMap.create()
  private val commonCourses: MultiMap<String, ModuleInfo> = MultiMap.create()

  private var currentConfiguration = switchOnExperimentalLessons

  val modules: Collection<IftModule>
    get() {
      prepareLangModules()
      return LangManager.getInstance().getLangSupport()?.let { languageCourses[it].map(ModuleInfo::module) } ?: emptyList()
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
        for (toolWindow in getAllLearnToolWindows()) {
          toolWindow.reinitViews()
        }
      }, this)
    }
  }

  fun clearModules() {
    languageCourses.clear()
    commonCourses.clear()
  }

  /**
   * @param projectWhereToOpen -- where to open projectWhereToOpen
   * @param forceStartLesson -- force start lesson without check for passed status (passed lessons will be opened as completed text)
   */
  fun openLesson(projectWhereToOpen: Project,
                 lesson: Lesson?,
                 startingWay: LessonStartingWay,
                 forceStartLesson: Boolean = false,
                 forceOpenLearningProject: Boolean = false,
  ) {
    LessonManager.instance.stopLesson()
    if (lesson == null) return //todo: remove null lessons
    OpenLessonActivities.openLesson(OpenLessonParameters(projectWhereToOpen, lesson, forceStartLesson, startingWay, forceOpenLearningProject))
  }

  fun findLessonById(lessonId: String): Lesson? {
    return lessonsForModules.firstOrNull { it.id == lessonId }
  }

  fun findCommonModules(commonCourseId: String): Collection<IftModule> {
    if (commonCourses.isEmpty) reloadCommonModules()
    return commonCourses[commonCourseId].map(ModuleInfo::module)
  }

  fun isModuleExternal(module: IftModule): Boolean {
    prepareLangModules()
    if (commonCourses.isEmpty) reloadCommonModules()
    return (languageCourses.values() + commonCourses.values()).any { it.isExternal && it.module.id == module.id }
  }

  private fun reloadLangModules() {
    val extensions = COURSE_MODULES_EP.extensions.filter { courseCanBeUsed(it.language) }
    for (e in extensions) {
      val langSupport = LangManager.getInstance().getLangSupportById(e.language)
      if (langSupport != null) {
        languageCourses.putValues(langSupport, createModules(e.instance, e.pluginDescriptor))
      }
    }
  }

  private fun reloadCommonModules() {
    val commonCoursesExtensions = COMMON_COURSE_MODULES_EP.extensions
    for (e in commonCoursesExtensions) {
      if (commonCourses[e.key].isEmpty()) {
        commonCourses.put(e.key, createModules(e.instance, e.pluginDescriptor))
      }
    }
  }

  private fun createModules(course: LearningCourse, pluginDescriptor: PluginDescriptor): Collection<ModuleInfo> {
    val isExternal = !getPluginInfoByDescriptor(pluginDescriptor).isDevelopedByJetBrains()
    return course.modules().map { ModuleInfo(it, isExternal) }
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