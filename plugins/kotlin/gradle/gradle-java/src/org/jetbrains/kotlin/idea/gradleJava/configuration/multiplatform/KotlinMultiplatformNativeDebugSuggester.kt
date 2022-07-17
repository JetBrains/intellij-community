// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration.multiplatform

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
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.NotNull
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

abstract class KotlinMultiplatformNativeDebugSuggester<TModel> {
    fun suggestNativeDebug(model: TModel?, resolverCtx: ProjectResolverContext) {
        if (!nativeDebugAdvertised && hasKotlinNativeHome(model)) {
            nativeDebugAdvertised = true
            suggestNativeDebug(resolverCtx.projectPath)
        }
    }

    protected abstract fun hasKotlinNativeHome(model: TModel?): Boolean

    companion object {
        private var nativeDebugAdvertised = false

        private var isNativeDebugSuggestionEnabled
            get() = PropertiesComponent.getInstance().getBoolean("isNativeDebugSuggestionEnabled", true)
            set(value) {
                PropertiesComponent.getInstance().setValue("isNativeDebugSuggestionEnabled", value)
            }

        private fun suggestNativeDebug(projectPath: String) {
            if (!PlatformUtils.isIdeaUltimate()) return

            val pluginId = PluginId.getId("com.intellij.nativeDebug")
            if (PluginManagerCore.isPluginInstalled(pluginId)) return

            if (!isNativeDebugSuggestionEnabled) return

            val projectManager = ProjectManager.getInstance()
            val project = projectManager.openProjects.firstOrNull { it.basePath == projectPath } ?: return

            notificationGroup
                .createNotification(
                    KotlinIdeaGradleBundle.message("notification.title.plugin.suggestion"),
                    KotlinIdeaGradleBundle.message("notification.text.native.debug.provides.debugger.for.kotlin.native"),
                    NotificationType.INFORMATION
                )
                .addAction(object : NotificationAction(KotlinIdeaGradleBundle.message("action.text.install")) {
                    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                        installAndEnable(project, setOf<@NotNull PluginId>(pluginId), true) { notification.expire() }
                    }
                })
                .addAction(object : NotificationAction(KotlinIdeaGradleBundle.message("action.text.dontShowAgain")) {
                    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                        isNativeDebugSuggestionEnabled = false
                        notification.expire()
                    }
                })
                .notify(project)
        }
    }
}

//object KotlinMPPNativeDebugSuggester : KotlinMultiplatformNativeDebugSuggester<KotlinMPPGradleModel>() {
//    override fun hasKotlinNativeHome(multiplatformModel: KotlinMPPGradleModel?): Boolean =
//        multiplatformModel?.kotlinNativeHome?.isNotEmpty() ?: false
//}