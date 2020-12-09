// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.util

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.intellij.ide.DataManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import training.lang.LangManager
import training.lang.LangSupport
import training.learn.CourseManager
import training.learn.interfaces.Lesson
import training.learn.lesson.LessonManager
import training.learn.lesson.LessonStateManager
import training.ui.LearnToolWindowFactory
import training.ui.LearningUiManager
import java.awt.Desktop
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

fun createNamedSingleThreadExecutor(name: String): ExecutorService =
  Executors.newSingleThreadExecutor(ThreadFactoryBuilder().setNameFormat(name).build())

fun findLanguageByID(id: String): Language? {
  val effectiveId = if (id.toLowerCase() == "cpp") {
    "ObjectiveC"
  }
  else {
    id
  }
  return Language.findLanguageByID(effectiveId)
}

fun createBalloon(@Nls text: String): Balloon = createBalloon(text, 3000)
fun createBalloon(@Nls text: String, delay: Long): Balloon =
  JBPopupFactory.getInstance()
    .createHtmlTextBalloonBuilder(text, null, UIUtil.getToolTipBackground(), null)
    .setHideOnClickOutside(true)
    .setCloseButtonEnabled(true)
    .setHideOnKeyOutside(true)
    .setAnimationCycle(0)
    .setFadeoutTime(delay)
    .createBalloon()

const val trainerPluginConfigName: String = "ide-features-trainer.xml"

val featureTrainerVersion: String by lazy {
  val featureTrainerPluginId = PluginManagerCore.getPluginByClassName(CourseManager::class.java.name)
  PluginManagerCore.getPlugin(featureTrainerPluginId)?.version ?: "UNKNOWN"
}

fun clearTrainingProgress() {
  LessonManager.instance.stopLesson()
  LessonStateManager.resetPassedStatus()
  for (toolWindow in LearnToolWindowFactory.learnWindowPerProject.values) {
    toolWindow.reinitViews()
    toolWindow.setModulesPanel()
  }
  LearningUiManager.activeToolWindow = null
}

fun resetPrimaryLanguage(activeLangSupport: LangSupport): Boolean {
  val old = LangManager.getInstance().getLangSupport()
  if (activeLangSupport != old) {
    LessonManager.instance.stopLesson()
    LangManager.getInstance().updateLangSupport(activeLangSupport)
    LearningUiManager.activeToolWindow?.setModulesPanel()
    return true
  }
  return false
}

fun findLanguageSupport(project: Project): LangSupport? {
  val langSupport = LangManager.getInstance().getLangSupport() ?: return null
  if (isLearningProject(project, langSupport)) {
    return langSupport
  }
  return null
}

fun isLearningProject(project: Project, langSupport: LangSupport): Boolean {
  return FileUtil.pathsEqual(project.basePath, LangManager.getInstance().getLearningProjectPath(langSupport))
}

val switchOnExperimentalLessons: Boolean
  get() = Registry.`is`("ift.experimental.lessons", false)

fun invokeActionForFocusContext(action: AnAction) {
  DataManager.getInstance().dataContextFromFocusAsync.onSuccess { dataContext ->
    invokeLater {
      val event = AnActionEvent.createFromAnAction(action, null, "IDE Features Trainer", dataContext)
      ActionUtil.performActionDumbAwareWithCallbacks(action, event, dataContext)
    }
  }
}

fun openLinkInBrowser(link: String) {
  val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
  if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
    desktop.browse(URI(link))
  }
}

fun lessonOpenedInProject(project: Project?): Lesson? {
  return if (LearnToolWindowFactory.learnWindowPerProject[project] != null) LessonManager.instance.currentLesson else null
}

fun getNextLessonForCurrent(): Lesson? {
  val lesson = LessonManager.instance.currentLesson ?: return null
  val lessonsForModules = CourseManager.instance.lessonsForModules
  val index = lessonsForModules.indexOf(lesson)
  if (index < 0 || index >= lessonsForModules.size - 1) return null
  return lessonsForModules[index + 1]
}

fun getPreviousLessonForCurrent(): Lesson? {
  val lesson = LessonManager.instance.currentLesson ?: return null
  val lessonsForModules = CourseManager.instance.lessonsForModules
  val index = lessonsForModules.indexOf(lesson)
  if (index <= 0) return null
  return lessonsForModules[index - 1]
}
