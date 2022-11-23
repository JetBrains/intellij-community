package com.intellij.grazie.ide.notification

import com.intellij.grazie.GrazieBundle
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationAction.createSimpleExpiring
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.util.application
import java.time.Duration

private val grazieProfessionalPluginId
  get() = PluginId.getId("com.intellij.grazie.pro")

private val isGrazieProfessionalInstalled
  get() = PluginManager.isPluginInstalled(grazieProfessionalPluginId)

private const val NOTIFICATION_SHOWN = "Grazie.Professional.Advertisement.Shown"
private val IGNORE_DELAY = Duration.ofDays(14).toMillis()

private const val INVOCATION_COUNT = "Grazie.Professional.Advertisement.Invoked"
private const val SHOW_AFTER_INVOCATIONS = 3

private fun shouldShow(): Boolean {
  val timestamp = PropertiesComponent.getInstance().getInt(NOTIFICATION_SHOWN, 0)
  if (timestamp < 0) {
    return false
  }
  val current = System.currentTimeMillis()
  return current - timestamp > IGNORE_DELAY
}

private fun markDelayed() {
  PropertiesComponent.getInstance().setValue(NOTIFICATION_SHOWN, System.currentTimeMillis().toString())
}

private fun markShown() {
  PropertiesComponent.getInstance().setValue(NOTIFICATION_SHOWN, "-1")
}

private fun obtainInvocationCount(): Int {
  return PropertiesComponent.getInstance().getInt(INVOCATION_COUNT, 0)
}

private fun updateInvocationCount(count: Int) {
  PropertiesComponent.getInstance().setValue(INVOCATION_COUNT, count.toString())
}

internal fun advertiseGrazieProfessional(project: Project) {
  if (isGrazieProfessionalInstalled || application.isUnitTestMode || !shouldShow()) {
    return
  }
  val invocationCount = obtainInvocationCount()
  if (invocationCount < SHOW_AFTER_INVOCATIONS) {
    updateInvocationCount(invocationCount + 1)
    return
  }
  markDelayed()
  invokeLater {
    val text = GrazieBundle.message("grazie.notification.pro.advertisement.text")
    val remindLaterAction = createSimpleExpiring(GrazieBundle.message("grazie.notification.pro.advertisement.remind.later.action.text")) {
      markDelayed()
    }
    val ignoreAction = createSimpleExpiring(GrazieBundle.message("grazie.notification.pro.advertisement.ignore.action.text")) {
      markShown()
    }
    val group = GrazieToastNotifications.GENERAL_GROUP
    group.createNotification(text, NotificationType.INFORMATION)
      .setTitle(GrazieBundle.message("grazie.notification.pro.advertisement.title"))
      .addAction(InstallGrazieProfessionalAction())
      .addAction(remindLaterAction)
      .addAction(ignoreAction)
      .setSuggestionType(true)
      .notify(project)
  }
}

private class InstallGrazieProfessionalAction: NotificationAction(GrazieBundle.message("grazie.notification.pro.advertisement.install.action.text")) {
  override fun actionPerformed(event: AnActionEvent, notification: Notification) {
    val project = event.project
    installAndEnable(project, setOf(grazieProfessionalPluginId), true) {
      notification.expire()
    }
  }
}
