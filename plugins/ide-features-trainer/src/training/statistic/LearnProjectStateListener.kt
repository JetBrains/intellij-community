// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.statistic

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.wm.impl.CloseProjectWindowHelper
import training.FeaturesTrainerIcons
import training.lang.LangManager
import training.learn.CourseManager
import training.learn.LearnBundle
import training.learn.course.Lesson
import training.statistic.StatisticBase.Companion.logShowNewLessonsNotificationState
import training.util.*

private class LearnProjectStateListener : ProjectManagerListener {
  override fun projectOpened(project: Project) {
    val languageId = LangManager.getInstance().getLanguageId() ?: return
    if (isLearningProject(project, languageId)) {
      CloseProjectWindowHelper.SHOW_WELCOME_FRAME_FOR_PROJECT.set(project, true)
      removeFromRecentProjects(project)
    }
    else {
      val learnProjectState = LearnProjectState.instance
      val way = learnProjectState.firstTimeOpenedWay
      if (way != null) {
        StatisticBase.logNonLearningProjectOpened(way)
        learnProjectState.firstTimeOpenedWay = null
      }
      considerNotifyAboutNewLessons(project)
    }
  }

  override fun projectClosingBeforeSave(project: Project) {
    val languageId = LangManager.getInstance().getLanguageId() ?: return
    if (isLearningProject(project, languageId) && !StatisticBase.isLearnProjectCloseLogged) {
      StatisticBase.logLessonStopped(StatisticBase.LessonStopReason.CLOSE_PROJECT)
    }
  }

  override fun projectClosed(project: Project) {
    val languageId = LangManager.getInstance().getLanguageId() ?: return
    if (isLearningProject(project, languageId)) {
      StatisticBase.isLearnProjectCloseLogged = false
      removeFromRecentProjects(project)
    }
  }

  private fun removeFromRecentProjects(project: Project) {
    val manager = RecentProjectsManagerBase.getInstanceEx()
    manager.getProjectPath(project)?.let { manager.removePath(it) }
  }
}

@State(name = "LearnProjectState", storages = [Storage(value = trainerPluginConfigName)], category = SettingsCategory.TOOLS)
internal class LearnProjectState : PersistentStateComponent<LearnProjectState> {
  var firstTimeOpenedWay: StatisticBase.LearnProjectOpeningWay? = null

  override fun getState(): LearnProjectState = this

  override fun loadState(state: LearnProjectState) {
    firstTimeOpenedWay = state.firstTimeOpenedWay
  }

  companion object {
    internal val instance: LearnProjectState
      get() = ApplicationManager.getApplication().getService(LearnProjectState::class.java)
  }
}

private const val NOTIFICATION_SESSION_COUNTER = "ift.show.new.lessons.notification.session.counter"

/** Need to show notification only once per session */
private var showingNotificationIsConsidered = false

private fun considerNotifyAboutNewLessons(project: Project) {
  if (!PropertiesComponent.getInstance().getBoolean(SHOW_NEW_LESSONS_NOTIFICATION, true)) {
    return
  }
  if (!enableLessonsAndPromoters || learningPanelWasOpenedInCurrentVersion || !iftPluginIsUsing || showingNotificationIsConsidered) {
    return
  }
  showingNotificationIsConsidered = true
  val newLessons = CourseManager.instance.newLessons
  if (newLessons.isEmpty()) {
    return
  }
  if (filterUnseenLessons(newLessons).isEmpty()) {
    return
  }

  val cooldown = 5
  val startCounter = 2
  val sessionCounter = PropertiesComponent.getInstance().getInt(NOTIFICATION_SESSION_COUNTER, startCounter) + 1
  PropertiesComponent.getInstance().setValue(NOTIFICATION_SESSION_COUNTER, sessionCounter % cooldown, startCounter)
  if (sessionCounter != cooldown) {
    return
  }
  notifyAboutNewLessons(project, newLessons)
}

private fun notifyAboutNewLessons(project: Project, newLessons: List<Lesson>) {
  val newLessonsCount = newLessons.filter { !it.passed }.size
  val previousOpenedVersion = CourseManager.instance.previousOpenedVersion
  StatisticBase.logNewLessonsNotification(newLessonsCount, previousOpenedVersion)
  val notification = iftNotificationGroup.createNotification(LearnBundle.message("notification.about.new.lessons"), NotificationType.INFORMATION)
  notification.icon = FeaturesTrainerIcons.FeatureTrainer

  notification.addAction(object : NotificationAction(LearnBundle.message("notification.show.new.lessons")) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      notification.expire()
      val toolWindow = learningToolWindow(project) ?: return
      StatisticBase.logShowNewLessonsEvent(newLessonsCount, previousOpenedVersion)
      toolWindow.show()
    }
  })
  notification.addAction(object : NotificationAction(LearnBundle.message("notification.do.not.show.new.lessons.notifications")) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      PropertiesComponent.getInstance().setValue(SHOW_NEW_LESSONS_NOTIFICATION, false, true)
      logShowNewLessonsNotificationState(newLessonsCount, previousOpenedVersion, false)
      notification.expire()
    }
  })
  notification.notify(project)
}
