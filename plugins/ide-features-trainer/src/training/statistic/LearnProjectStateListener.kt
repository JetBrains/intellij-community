// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.statistic

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
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
    val langSupport = LangManager.getInstance().getLangSupport() ?: return
    if (isLearningProject(project, langSupport)) {
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
    val langSupport = LangManager.getInstance().getLangSupport() ?: return
    if (isLearningProject(project, langSupport) && !StatisticBase.isLearnProjectCloseLogged) {
      StatisticBase.logLessonStopped(StatisticBase.LessonStopReason.CLOSE_PROJECT)
    }
  }

  override fun projectClosed(project: Project) {
    val langSupport = LangManager.getInstance().getLangSupport() ?: return
    if (isLearningProject(project, langSupport)) {
      StatisticBase.isLearnProjectCloseLogged = false
      removeFromRecentProjects(project)
    }
  }

  private fun removeFromRecentProjects(project: Project) {
    val manager = RecentProjectsManagerBase.instanceEx
    manager.getProjectPath(project)?.let { manager.removePath(it) }
  }
}

@State(name = "LearnProjectState", storages = [Storage(value = trainerPluginConfigName)])
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

private fun considerNotifyAboutNewLessons(project: Project) {
  if (!PropertiesComponent.getInstance().getBoolean(SHOW_NEW_LESSONS_NOTIFICATION, true)) {
    return
  }
  if (learningPanelWasOpenedInCurrentVersion || !iftPluginIsUsing) {
    return
  }
  val newLessons = CourseManager.instance.newLessons
  if (newLessons.isEmpty() || newLessons.any { it.passed }) {
    return
  }
  notifyAboutNewLessons(project, newLessons)
}


private fun notifyAboutNewLessons(project: Project, newLessons: List<Lesson>) {
  val newLessonsCount = newLessons.filter { !it.passed }.size
  val previousOpenedVersion = CourseManager.instance.previousOpenedVersion
  StatisticBase.logNewLessonsNotification(newLessonsCount, previousOpenedVersion)
  val notificationGroup = NotificationGroup.findRegisteredGroup("IDE Features Trainer")
                          ?: error("Not found notificationGroup for IDE Features Trainer")
  val notification = notificationGroup.createNotification(LearnBundle.message("notification.about.new.lessons"), NotificationType.INFORMATION)
  notification.icon = FeaturesTrainerIcons.Img.FeatureTrainer

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
