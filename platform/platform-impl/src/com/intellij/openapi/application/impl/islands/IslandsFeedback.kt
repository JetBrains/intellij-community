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
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.milliseconds

internal class IslandsFeedback : ProjectActivity {
  internal companion object {
    internal fun isIslandTheme(): Boolean {
      return isIslandTheme(LafManager.getInstance().currentUIThemeLookAndFeel?.id ?: return false)
    }

    internal fun isIslandTheme(themeId: String) = themeId == "Islands Dark" || themeId == "Islands Light"

    internal fun getReadMoreUrl() = "https://blog.jetbrains.com/platform/2025/09/islands-theme-the-new-look-coming-to-jetbrains-ides/"

    internal fun getFeedbackUrl() = "https://surveys.jetbrains.com/s3/Feedback-Survey-About-IslandsUI-EAP"

    @Volatile
    private var myFirstProject = true
  }

  override suspend fun execute(project: Project) {
    if (myFirstProject) {
      myFirstProject = false

      if (!ApplicationManager.getApplication().isUnitTestMode &&
          !ApplicationManager.getApplication().isHeadlessEnvironment &&
          !AppMode.isRemoteDevHost() &&
          !Registry.`is`("llm.riderNext.enabled", false) && ExperimentalUI.isNewUI()) {

        handleFeedback(project)
      }
    }
  }
}

private fun handleFeedback(project: Project) {
  val properties = PropertiesComponent.getInstance()
  val showFeedbackValue = properties.getValue("ide.islands.show.feedback2")

  if (showFeedbackValue != null) {
    if (showFeedbackValue == "show.promo") {
      showPromoNotification(WeakReference(project))
    }
    else {
      showNotification()
      return
    }
  }

  val showFeedbackTime = properties.getLong("ide.islands.show.feedback2.time", 0)
  if (showFeedbackTime > 0) {
    val themeId = LafManager.getInstance().currentUIThemeLookAndFeel.id
    if (!IslandsFeedback.isIslandTheme(themeId)) {
      showNotification()
      return
    }

    scheduleNotification(showFeedbackTime)
  }
  else {
    val themeId = LafManager.getInstance().currentUIThemeLookAndFeel?.id ?: return

    if (IslandsFeedback.isIslandTheme(themeId)) {
      val currentTime = System.currentTimeMillis()

      properties.setValue("ide.islands.show.feedback2.time", currentTime.toString())
      scheduleNotification(currentTime)
    }
  }

  val connection = ApplicationManager.getApplication().messageBus.connect()

  connection.subscribe(LafManagerListener.TOPIC, LafManagerListener { manager ->
    if (properties.getValue("ide.islands.show.feedback2") == "done") {
      connection.disconnect()
      return@LafManagerListener
    }

    val themeId = manager.currentUIThemeLookAndFeel.id

    if (properties.getLong("ide.islands.show.feedback2.time", 0) == 0L) {
      if (IslandsFeedback.isIslandTheme(themeId)) {
        val currentTime = System.currentTimeMillis()

        properties.setValue("ide.islands.show.feedback2.time", currentTime.toString())
        scheduleNotification(currentTime)
      }
    }
    else if (!IslandsFeedback.isIslandTheme(themeId)) {
      connection.disconnect()
      showNotification()
    }
  })
}

private fun showNotification() {
  val properties = PropertiesComponent.getInstance()
  if (properties.getValue("ide.islands.show.feedback2") == "done") {
    return
  }

  clearProperties(properties)

  val notification = Notification("Feedback In IDE", IdeBundle.message("ide.islands.share.feedback.title"),
                                  IdeBundle.message("ide.islands.share.feedback.message"), NotificationType.INFORMATION)

  notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("ide.islands.share.feedback.button")) {
    BrowserUtil.browse(IslandsFeedback.getFeedbackUrl())
  })

  notification.addAction(NotificationAction.createSimpleExpiring(CommonBundle.message("button.decline")) {})

  notification.setSuggestionType(true).setImportantSuggestion(true).setIcon(AllIcons.Ide.Feedback).notify(null)
}

private fun showPromoNotification(projectRef: WeakReference<Project>) {
  val properties = PropertiesComponent.getInstance()
  clearProperties(properties)
  properties.unsetValue("ide.islands.show.feedback2")

  val notification = Notification("STICKY:Feedback In IDE", IdeBundle.message("ide.islands.share.feedback.promo.title"),
                                  IdeBundle.message("ide.islands.share.feedback.promo.message"), NotificationType.INFORMATION)

  notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("got.it.button.name")) {})

  notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("ide.islands.read.more")) {
    BrowserUtil.browse(IslandsFeedback.getReadMoreUrl())
  })

  notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("ide.islands.switch.theme")) {
    ShowSettingsUtil.getInstance().showSettingsDialog(projectRef.get(), AppearanceConfigurable::class.java)
  })

  notification.setSuggestionType(true).setImportantSuggestion(true).setIcon(AllIcons.Ide.Gift).setAddExtraAction(true).notify(null)
}

private fun clearProperties(properties: PropertiesComponent) {
  properties.setValue("ide.islands.show.feedback2", "done")
  properties.unsetValue("ide.islands.show.feedback2.time")
}

private fun scheduleNotification(time: Long) {
  val delta = time + 48 * 3600 * 1000 - System.currentTimeMillis()

  if (delta <= 0) {
    showNotification()
    return
  }

  service<ScopeHolder>().scope.launch {
    delay(delta.milliseconds)
    showNotification()
  }
}

@Service(Service.Level.APP)
private class ScopeHolder(val scope: CoroutineScope)