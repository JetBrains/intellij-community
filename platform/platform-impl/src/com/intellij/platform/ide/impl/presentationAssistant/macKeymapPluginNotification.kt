// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable

fun showInstallMacKeymapPluginNotification(pluginId: PluginId) {
    val title = "Shortcuts for macOS are not shown"
    val content = "In order to show shortcuts for macOS you need to install 'macOS Keymap' plugin"
    val notification = Notification("Presentation Assistant", title, content, NotificationType.INFORMATION)
    notification.addAction(object : AnAction("Install Plugin") {
        override fun actionPerformed(e: AnActionEvent) {
            installAndEnable(null, setOf(pluginId), false) { notification.expire() }
        }
    })
    notification.addAction(object : AnAction("Do Not Show macOS Shortcuts") {
        override fun actionPerformed(e: AnActionEvent) {
            getPresentationAssistant().configuration.alternativeKeymap = null
            notification.expire()
        }
    })
    notification.notify(null)
}