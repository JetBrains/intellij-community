// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.toolchain

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.service.project.GradleNotification
import org.jetbrains.plugins.gradle.service.project.GradleNotificationIdsHolder
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.awt.Desktop
import java.net.URI

private const val IGNORE_DAEMON_TOOLCHAIN_MIGRATION = "IGNORE_DAEMON_TOOLCHAIN_MIGRATION"

object GradleDaemonToolchainMigrateNotification {

    fun show(project: Project, externalProjectPath: String) {
        if (isDaemonToolchainMigrationIgnored) return

        GradleNotification.gradleNotificationGroup.createNotification(
            title = GradleBundle.message("gradle.notifications.daemon.toolchain.migration.title", project.name),
            content = GradleBundle.message("gradle.notifications.daemon.toolchain.migration.description"),
            type = NotificationType.INFORMATION
        ).addAction(
            NotificationAction.create(GradleBundle.message("gradle.notifications.daemon.toolchain.migration.info.action.text")) { _, _ ->
                if (Desktop.isDesktopSupported()) {
                    val url = GradleBundle.message("gradle.notifications.daemon.toolchain.migration.info.url")
                    Desktop.getDesktop().browse(URI(url))
                }
            }
        ).addAction(NotificationAction.create(GradleBundle.message("gradle.notifications.daemon.toolchain.migration.accept.action.text")) { _, notification ->
            isDaemonToolchainMigrationIgnored = true
            notification.expire()
            GradleDaemonToolchainMigrationService(project).startMigration(externalProjectPath)
        }).addAction(NotificationAction.create(GradleBundle.message("gradle.notifications.daemon.toolchain.migration.cancel.action.text")) { _, notification ->
            isDaemonToolchainMigrationIgnored = true
            notification.expire()
        }).setDisplayId(GradleNotificationIdsHolder.daemonToolchainMigration)
            .notify(project)
    }

    private var isDaemonToolchainMigrationIgnored: Boolean
        get() = PropertiesComponent.getInstance().getBoolean(IGNORE_DAEMON_TOOLCHAIN_MIGRATION, false)
        set(value) = PropertiesComponent.getInstance().setValue(IGNORE_DAEMON_TOOLCHAIN_MIGRATION, value)

}