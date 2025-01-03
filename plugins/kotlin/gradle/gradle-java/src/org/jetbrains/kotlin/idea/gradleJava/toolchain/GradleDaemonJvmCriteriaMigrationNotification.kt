// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.toolchain

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.propComponentProperty
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.service.project.GradleNotification
import org.jetbrains.plugins.gradle.service.project.GradleNotificationIdsHolder
import org.jetbrains.plugins.gradle.util.GradleBundle


object GradleDaemonJvmCriteriaMigrationNotification {

    private const val DISABLE_DAEMON_TOOLCHAIN_MIGRATION = "GradleDaemonJvmCriteriaMigrationNotification.isNotificationDisabled"

    fun show(project: Project, externalProjectPath: String) {
        var isNotificationDisabled: Boolean by propComponentProperty(project, DISABLE_DAEMON_TOOLCHAIN_MIGRATION, false)

        if (isNotificationDisabled) return

        GradleNotification.gradleNotificationGroup.createNotification(
            title = GradleBundle.message("gradle.notifications.daemon.toolchain.migration.title", project.name),
            content = GradleBundle.message("gradle.notifications.daemon.toolchain.migration.description"),
            type = NotificationType.INFORMATION
        )
            .setDisplayId(GradleNotificationIdsHolder.daemonToolchainMigration)
            .addAction(NotificationAction.createSimple(GradleBundle.message("gradle.notifications.daemon.toolchain.migration.info.action.text")) {
                BrowserUtil.browse(GradleBundle.message("gradle.notifications.daemon.toolchain.migration.info.url"))
            })
            .addAction(NotificationAction.createSimpleExpiring(GradleBundle.message("gradle.notifications.daemon.toolchain.migration.accept.action.text")) {
                isNotificationDisabled = true
                GradleDaemonJvmCriteriaMigrationHelper.migrateToDaemonJvmCriteria(project, externalProjectPath)
            })
            .addAction(NotificationAction.createSimpleExpiring(GradleBundle.message("gradle.notifications.daemon.toolchain.migration.cancel.action.text")) {
                isNotificationDisabled = true
            })
            .notify(project)
    }
}