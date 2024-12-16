// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions.internal

import com.intellij.facet.FacetManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.provider.getEelApi
import org.jetbrains.kotlin.idea.KotlinIdeaBundle
import org.jetbrains.kotlin.idea.base.compilerPreferences.configuration.KotlinCompilerConfigurableTab
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerWorkspaceSettings
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import java.nio.file.Path

/**
 * TODO: issue
 */
class KotlinNonLocalProjectDetector : ProjectActivity {
    private val DO_NOT_ASK_AGAIN = "KotlinNonLocalProjectDetector.do.not.ask.again"

    override suspend fun execute(project: Project) {
        if (!allowedToShowNotification()) return
        if (!containsEnabledKotlinDaemon(project)) return
        if (isLocalProject(project)) return
        if (!containsKotlinModule(project)) return


        val notification = NotificationGroupManager.getInstance().getNotificationGroup("Kotlin Daemon Disable Advice")
            .createNotification(
                KotlinIdeaBundle.message("notification.content.kotlin.daemon.in.non.local.project"),
                NotificationType.WARNING
            )
        notification.addAction(getDisableDaemonNotification(project))
        notification.addAction(getDoNotShowAgainNotification())
        notification.addAction(getShowSettingsNotification(project))
        notification.notify(project)
    }

    private fun getDoNotShowAgainNotification(): NotificationAction =
        object : NotificationAction(KotlinIdeaBundle.message("notification.content.do.not.show.again")) {
            override fun actionPerformed(
                e: AnActionEvent, notification: Notification
            ) {
                PropertiesComponent.getInstance().setValue(DO_NOT_ASK_AGAIN, true)
                notification.expire()
            }
        }

    private suspend fun isLocalProject(project: Project): Boolean {
        val projectPath = project.basePath ?: return true
        val eel = Path.of(projectPath).getEelApi()
        return eel is LocalEelApi
    }

    private fun containsKotlinModule(project: Project): Boolean {
        val modules = ModuleManager.getInstance(project).modules
        return modules.any { FacetManager.getInstance(it).getFacetByType(KotlinFacetType.TYPE_ID) != null }
    }

    private fun containsEnabledKotlinDaemon(project: Project): Boolean {
        val workspaceSettings = KotlinCompilerWorkspaceSettings.getInstance(project)
        return workspaceSettings.enableDaemon
    }

    private fun getDisableDaemonNotification(project: Project): NotificationAction {
        return object : NotificationAction(KotlinIdeaBundle.message("notification.content.disable.daemon")) {
            override fun actionPerformed(
                e: AnActionEvent, notification: Notification
            ) {
                KotlinCompilerWorkspaceSettings.getInstance(project).enableDaemon = false
                notification.expire()
            }
        }
    }

    private fun getShowSettingsNotification(project: Project): NotificationAction {
        return object : NotificationAction(KotlinIdeaBundle.message("notification.content.show.settings")) {
            override fun actionPerformed(
                e: AnActionEvent, notification: Notification
            ) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, KotlinCompilerConfigurableTab::class.java)
                notification.expire()
            }
        }
    }

    private fun allowedToShowNotification(): Boolean {
        val propertiesComponent = PropertiesComponent.getInstance()
        return !propertiesComponent.getBoolean(DO_NOT_ASK_AGAIN, false)
    }
}