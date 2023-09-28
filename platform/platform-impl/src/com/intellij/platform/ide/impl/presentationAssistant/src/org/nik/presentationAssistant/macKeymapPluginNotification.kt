/*
 * Copyright 2000-2021 Nikolay Chashnikov.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nik.presentationAssistant

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