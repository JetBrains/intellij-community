// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradle.configuration

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
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

private var isKotlinJSInspectionsPackSuggestionEnabled: Boolean
    get() {
        val service = DontShowAgainKotlinAdditionPluginSuggestionService.getInstance()
        return !service.state.dontShowAgainKotlinJSInspectionPackSuggestion
    }
    set(value) {
        val service = DontShowAgainKotlinAdditionPluginSuggestionService.getInstance()
        service.state.dontShowAgainKotlinJSInspectionPackSuggestion = !value
    }

fun suggestNativeDebug(projectPath: String) {
    if (!isNativeDebugSuggestionEnabled) return
    suggestPluginInstall(
        projectPath = projectPath,
        notificationContent = KotlinIdeaGradleBundle.message("notification.text.native.debug.provides.debugger.for.kotlin.native"),
        pluginId = PluginId.getId("com.intellij.nativeDebug"),
        onDontShowAgainActionPerformed = { isNativeDebugSuggestionEnabled = false }
    )
}

fun suggestKotlinJsInspectionPackPlugin(projectPath: String) {
    if (!isKotlinJSInspectionsPackSuggestionEnabled) return
    suggestPluginInstall(
        projectPath = projectPath,
        notificationContent = KotlinIdeaGradleBundle.message("notification.text.kotlin.js.inspections.pack.provides.inspections.and.quick.fixes.for.kotlin.js"),
        pluginId = PluginId.getId("org.jetbrains.kotlin-js-inspection-pack-plugin"),
        onDontShowAgainActionPerformed = { isKotlinJSInspectionsPackSuggestionEnabled = false }
    )
}

private fun suggestPluginInstall(
    projectPath: String,
    @NlsContexts.NotificationContent notificationContent: String,
    pluginId: PluginId,
    onDontShowAgainActionPerformed: () -> Unit = { }
) {
    if (!PlatformUtils.isIdeaUltimate()) return
    if (PluginManagerCore.isPluginInstalled(pluginId)) return

    val projectManager = ProjectManager.getInstance()
    val project = projectManager.openProjects.firstOrNull { it.basePath == projectPath } ?: return

    notificationGroup
        .createNotification(KotlinIdeaGradleBundle.message("notification.title.plugin.suggestion"), notificationContent, NotificationType.INFORMATION)
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