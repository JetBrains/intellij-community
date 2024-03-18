// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.idea.ActionsBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.Change
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurationService
import org.jetbrains.kotlin.idea.configuration.KotlinSetupEnvironmentNotificationProvider
import org.jetbrains.kotlin.idea.configuration.getAbleToRunConfigurators
import org.jetbrains.kotlin.idea.configuration.ui.changes.KotlinConfiguratorChangesDialog
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import java.awt.Desktop
import java.net.URI

@Service(Service.Level.PROJECT)
class KotlinAutoConfigurationNotificationHolder(private val project: Project) : Disposable {
    companion object {
        fun getInstance(project: Project): KotlinAutoConfigurationNotificationHolder {
            return project.service()
        }
    }

    private class NotificationData(val moduleName: String, val changes: List<Change>)

    private var shownNotification: Notification? = null
    private var notificationData: NotificationData? = null


    fun showAutoConfiguredNotification(moduleName: String?, changes: List<Change>?) {
        // shownNotification should never be non-null here as we should only ever have one notification at a time.
        // If more are shown then this is a leak of the notification somewhere in our code.
        shownNotification?.expire()
        if (moduleName != null && changes != null) {
            notificationData = NotificationData(moduleName, changes)
        } else {
            notificationData = null
        }

        val notificationText = if (moduleName != null) {
            KotlinProjectConfigurationBundle.message("auto.configure.kotlin.notification", moduleName)
        } else {
            KotlinProjectConfigurationBundle.message("auto.configure.kotlin.notification.no-module")
        }
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Configure Kotlin")
            .createNotification(
                title = KotlinProjectConfigurationBundle.message("auto.configure.kotlin"),
                content = notificationText,
                type = NotificationType.INFORMATION,
            )
        notification.addAction(undoAction(project))
        if (changes != null) {
            notification.addAction(viewAppliedChangesAction(changes))
        }
        notification.notify(project)
        shownNotification = notification
    }

    private val browseKotlinGradleConfiguration = NotificationAction.create(
        KotlinProjectConfigurationBundle.message("auto.configure.kotlin.documentation.gradle")
    ) { _, _ ->
        if (Desktop.isDesktopSupported()) {
            val url = KotlinProjectConfigurationBundle.message("auto.configure.kotlin.documentation.gradle.url")
            Desktop.getDesktop().browse(URI(url))
        }
    }

    fun showAutoConfigurationUndoneNotification(module: Module?) {
        val existingNotificationData = notificationData
        shownNotification?.expire()

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Configure Kotlin")
            .createNotification(
                title = KotlinProjectConfigurationBundle.message("auto.configure.kotlin.undone"),
                content = "",
                type = NotificationType.INFORMATION,
            )
        module?.let {
            notification.addAction(configureKotlinManuallyAction(it))
        }
        notification.addAction(browseKotlinGradleConfiguration)
        notification.notify(project)
        shownNotification = notification
        // Needs to be set again because the other notification might expire, which will set the notificationData to null
        notificationData = existingNotificationData
    }

    fun reshowAutoConfiguredNotification(module: Module?) {
        val existingNotificationData = notificationData
        shownNotification?.expire()

        showAutoConfiguredNotification(module?.name, existingNotificationData?.changes)
    }

    /**
     * This is a variable keeping track if the manual configuration was actually started after the manual configuration dialog
     * was invoked.
     * The current functions do not return feedback about whether the user chose to configure the modules or pressed cancel in the dialog,
     * so this variable is used as a workaround.
     */
    private var manualConfigurationStarted: Boolean = false
    fun onManualConfigurationCompleted() {
        manualConfigurationStarted = true
    }

    private fun configureKotlinManuallyAction(module: Module) = NotificationAction.create(
        KotlinProjectConfigurationBundle.message("configure.kotlin.manually")
    ) { e, notification ->
        if (KotlinProjectConfigurationService.getInstance(project).isGradleSyncInProgress()) {
            Messages.showWarningDialog(
                project,
                KotlinProjectConfigurationBundle.message("auto.configure.kotlin.wait.gradle.sync.finished"),
                KotlinProjectConfigurationBundle.message("auto.configure.kotlin.wait.gradle.sync.finished.title")
            )
            return@create
        }

        fun expireNotificationIfConfigured() {
            if (manualConfigurationStarted) {
                notification.expire()
            }
        }

        val configurators = getAbleToRunConfigurators(module).toList()
        manualConfigurationStarted = false
        if (configurators.size > 1) {
            KotlinSetupEnvironmentNotificationProvider
                .createConfiguratorsPopup(project, configurators) {
                    expireNotificationIfConfigured()
                }.showInBestPositionFor(e.dataContext)
        } else if (configurators.size == 1) {
            configurators.first().configure(project, emptyList())
        }
        expireNotificationIfConfigured()
    }

    private fun viewAppliedChangesAction(changes: List<Change>) = NotificationAction.create(
        KotlinProjectConfigurationBundle.message("view.code.differences.action")
    ) { _, _ ->
        KotlinConfiguratorChangesDialog(project, changes)
    }


    private fun showUndoErrorMessage(project: Project) {
        Messages.showErrorDialog(
            project,
            KotlinProjectConfigurationBundle.message("auto.configure.kotlin.undo.not-possible.content"),
            KotlinProjectConfigurationBundle.message("auto.configure.kotlin.undo.not-possible.title")
        )
    }

    private fun undoAction(project: Project) = NotificationAction.create(
        KotlinProjectConfigurationBundle.message("undo.configuration.action")
    ) { _, notification ->
        val undoManager = UndoManager.getInstance(project)
        if (undoManager.isUndoAvailable(null)) {
            val undoActionName = undoManager.getUndoActionNameAndDescription(null).first
            val undoAutoconfigureKotlinName =
                ActionsBundle.message("action.undo.text", KotlinIdeaGradleBundle.message("command.name.configure.kotlin.automatically"))
            if (undoActionName == undoAutoconfigureKotlinName) {
                undoManager.undo(null)
            } else {
                showUndoErrorMessage(project)
            }
        } else {
            showUndoErrorMessage(project)
        }
    }

    private fun expireShownNotification() {
        shownNotification?.expire()
        shownNotification = null

        // We unset the notificationData here to avoid leaking the data.
        // When a user closes the notification, this data is unset and cannot be used anymore.
        notificationData = null
    }

    override fun dispose() {
        expireShownNotification()
    }
}