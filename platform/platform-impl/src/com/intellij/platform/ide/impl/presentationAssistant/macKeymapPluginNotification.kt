// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.ide.IdeBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable

fun showInstallMacKeymapPluginNotification(pluginId: PluginId) {
  val title = IdeBundle.message("presentation.assistant.notification.title")
  val content = IdeBundle.message("presentation.assistant.notification.content")
  val notification = Notification("Presentation Assistant", title, content, NotificationType.INFORMATION)
  notification.addAction(object : AnAction(IdeBundle.message("presentation.assistant.notification.action.install")) {
    override fun actionPerformed(e: AnActionEvent) {
      installAndEnable(null, setOf(pluginId), false) { notification.expire() }
    }
  })
  notification.addAction(object : AnAction(IdeBundle.message("presentation.assistant.notification.action.hide")) {
    override fun actionPerformed(e: AnActionEvent) {
      service<PresentationAssistant>().configuration.showAlternativeKeymap = false
      notification.expire()
    }
  })
  notification.notify(null)
}