// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradle.configuration

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.notificationGroup
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.NotNull
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.kotlin.idea.gradle.configuration.state.DontShowAgainKotlinAdditionPluginSuggestionService

private var isNativeDebugSuggestionEnabled: Boolean
    get() {
        val service = DontShowAgainKotlinAdditionPluginSuggestionService.getInstance()
        return !service.state.dontShowAgainKotlinNativeDebuggerPluginSuggestion
    }
    set(value) {
        val service = DontShowAgainKotlinAdditionPluginSuggestionService.getInstance()
        service.state.dontShowAgainKotlinNativeDebuggerPluginSuggestion = !value
    }

fun suggestNativeDebug(projectPath: String) {
    if (!isNativeDebugSuggestionEnabled) return
    suggestPluginInstall(
        projectPath = projectPath,
        notificationContent = KotlinIdeaGradleBundle.message("notification.text.native.debug.provides.debugger.for.kotlin.native"),
        displayId = "kotlin.native.debug",
        pluginId = PluginId.getId("com.intellij.nativeDebug"),
        onDontShowAgainActionPerformed = { isNativeDebugSuggestionEnabled = false },
        onlyForUltimate = true,
    )
}

private fun suggestPluginInstall(
    projectPath: String,
    @NlsContexts.NotificationContent notificationContent: String,
    pluginId: PluginId,
    displayId: String,
    onDontShowAgainActionPerformed: () -> Unit = { },
    onlyForUltimate: Boolean = false,
) {
    if (onlyForUltimate && !PlatformUtils.isIdeaUltimate()) return
    if (PluginManagerCore.isPluginInstalled(pluginId)) return

    val projectManager = ProjectManager.getInstance()
    val project = projectManager.openProjects.firstOrNull { it.basePath == projectPath } ?: return

    notificationGroup
        .createNotification(KotlinIdeaGradleBundle.message("notification.title.plugin.suggestion"), notificationContent, NotificationType.INFORMATION)
        .setDisplayId(displayId)
        .setSuggestionType(true)
        .addAction(object : NotificationAction(KotlinIdeaGradleBundle.message("action.text.install")) {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                installAndEnable(project, setOf<@NotNull PluginId>(pluginId)) { notification.expire() }
            }
        })
        .addAction(object : NotificationAction(KotlinIdeaGradleBundle.message("action.text.dontShowAgain")) {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                onDontShowAgainActionPerformed()
                notification.expire()
            }
        })
        .notify(project)
}
