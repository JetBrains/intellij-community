// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.AppearanceConfigurable
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.AppMode
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.TimeUnit

internal class IslandsFeedback : ProjectActivity {
  internal companion object {
    internal fun isIslandTheme(): Boolean {
      return isIslandTheme(LafManager.getInstance().currentUIThemeLookAndFeel?.id ?: return false)
    }

    internal fun isIslandTheme(themeId: String) = isOneIslandTheme(themeId) || isManyIslandTheme(themeId)

    internal fun isOneIslandTheme(themeId: String) = themeId == "One Island Dark" || themeId == "One Island Light"

    internal fun isManyIslandTheme(themeId: String) = themeId == "Many Islands Dark" || themeId == "Many Islands Light"

    internal fun getReadMoreUrl() = "https://blog.jetbrains.com/platform/2025/06/testing-a-fresh-look-for-jetbrains-ides/"

    internal fun getFeedbackUrl(oneIsland: Boolean): String {
      return if (oneIsland) "https://surveys.jetbrains.com/s3/JetBrains-EAP-UI-Feedback-Survey" else "https://surveys.jetbrains.com/s3/Feedback-Survey-About-UI-EAP"
    }

    private var myFirstProject = !Registry.`is`("llm.riderNext.enabled", false) && ExperimentalUI.isNewUI() &&
                                 !ApplicationManager.getApplication().isUnitTestMode &&
                                 !ApplicationManager.getApplication().isHeadlessEnvironment &&
                                 !AppMode.isRemoteDevHost()
  }

  override suspend fun execute(project: Project) {
    if (myFirstProject) {
      myFirstProject = false
      handleFeedback(project)
    }
  }

  private fun handleFeedback(project: Project) {
    val properties = PropertiesComponent.getInstance()
    val showFeedbackValue = properties.getValue("ide.islands.show.feedback")

    if (showFeedbackValue != null) {
      if (showFeedbackValue == "show.promo") {
        showPromoNotification(project)
      }
      else {
        showNotification(showFeedbackValue == "one")
        return
      }
    }

    val showFeedbackTime = properties.getLong("ide.islands.show.feedback.time", 0)
    if (showFeedbackTime > 0) {
      val themeId = LafManager.getInstance().currentUIThemeLookAndFeel.id
      if (!isOneIslandTheme(themeId) && !isManyIslandTheme(themeId)) {
        showNotification(properties.getBoolean("ide.islands.show.feedback.theme"))
        return
      }

      scheduleNotification(properties.getBoolean("ide.islands.show.feedback.theme"), showFeedbackTime)
    }
    else {
      val themeId = LafManager.getInstance().currentUIThemeLookAndFeel?.id ?: return
      val oneIslandTheme = isOneIslandTheme(themeId)

      if (oneIslandTheme || isManyIslandTheme(themeId)) {
        val currentTime = System.currentTimeMillis()

        properties.setValue("ide.islands.show.feedback.time", currentTime.toString())
        properties.setValue("ide.islands.show.feedback.theme", oneIslandTheme)
        scheduleNotification(oneIslandTheme, currentTime)
      }
    }

    val connection = ApplicationManager.getApplication().messageBus.connect()

    connection.subscribe(LafManagerListener.TOPIC, LafManagerListener { manager ->
      val themeId = manager.currentUIThemeLookAndFeel.id

      if (properties.getLong("ide.islands.show.feedback.time", 0) == 0L) {
        if (isOneIslandTheme(themeId) || isManyIslandTheme(themeId)) {
          val currentTime = System.currentTimeMillis()
          val oneIslandTheme = isOneIslandTheme(themeId)

          properties.setValue("ide.islands.show.feedback.time", currentTime.toString())
          properties.setValue("ide.islands.show.feedback.theme", oneIslandTheme)
          scheduleNotification(oneIslandTheme, currentTime)
        }
      }
      else {
        val oneIslandTheme = isOneIslandTheme(themeId)

        if (oneIslandTheme || isManyIslandTheme(themeId)) {
          properties.setValue("ide.islands.show.feedback.theme", oneIslandTheme)
        }
        else {
          properties.setValue("ide.islands.show.feedback", if (properties.getBoolean("ide.islands.show.feedback.theme")) "one" else "many")
          connection.disconnect()
        }
      }
    })
  }

  private fun showNotification(oneIsland: Boolean) {
    val properties = PropertiesComponent.getInstance()
    if (properties.getValue("ide.islands.show.feedback") == "done") {
      return
    }

    clearProperties(properties)

    val notification = Notification("Feedback In IDE", IdeBundle.message("ide.islands.share.feedback.title"),
                                    IdeBundle.message("ide.islands.share.feedback.message"), NotificationType.INFORMATION)

    notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("ide.islands.share.feedback.button")) {
      BrowserUtil.browse(getFeedbackUrl(oneIsland))
    })

    notification.addAction(NotificationAction.createSimpleExpiring(CommonBundle.message("button.decline")) {})

    notification.setSuggestionType(true).setImportantSuggestion(true).setIcon(AllIcons.Ide.Feedback).notify(null)
  }

  private fun showPromoNotification(project: Project) {
    val properties = PropertiesComponent.getInstance()
    clearProperties(properties)
    properties.unsetValue("ide.islands.show.feedback")

    val notification = Notification("STICKY:Feedback In IDE", IdeBundle.message("ide.islands.share.feedback.promo.title"),
                                    IdeBundle.message("ide.islands.share.feedback.promo.message"), NotificationType.INFORMATION)

    notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("got.it.button.name")) {})

    notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("ide.islands.read.more")) {
      BrowserUtil.browse(getReadMoreUrl())
    })

    notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("ide.islands.switch.theme")) {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, AppearanceConfigurable::class.java)
    })

    notification.setSuggestionType(true).setImportantSuggestion(true).setIcon(AllIcons.Ide.Gift).setAddExtraAction(true).notify(null)
  }

  private fun clearProperties(properties: PropertiesComponent) {
    properties.setValue("ide.islands.show.feedback", "done")
    properties.unsetValue("ide.islands.show.feedback.time")
    properties.unsetValue("ide.islands.show.feedback.theme")
  }

  private fun scheduleNotification(oneIsland: Boolean, time: Long) {
    val delta = time + 48 * 3600 * 1000 - System.currentTimeMillis()

    if (delta <= 0) {
      showNotification(oneIsland)
      return
    }

    AppExecutorUtil.getAppScheduledExecutorService().schedule(Runnable { showNotification(oneIsland) }, delta, TimeUnit.MILLISECONDS)
  }
}