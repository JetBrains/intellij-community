// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration.notifications

import com.intellij.ide.util.RunOnceUtil
import com.intellij.notification.BrowseNotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinIcons
import java.time.LocalDate
import java.util.*

internal fun showEapSurveyNotification(project: Project) {
    if (LocalDate.now() > LocalDate.of(/* year = */ 2022, /* month = */ 5, /* dayOfMonth = */ 8)) return

    val compilerVersion = KotlinCompilerVersion.VERSION.toLowerCase()
    if (!compilerVersion.contains("1.7.0-beta")) return

    // Only beta 1. Yes, it is a bit ugly, but we don't have nice IdeKotlinVersion in old kt branches. And yes, I hope we won't have beta3.
    if (compilerVersion.contains("1.7.0-beta2")) return

    RunOnceUtil.runOnceForApp("kotlin.eap.survey.was.shown.once") {
        @Suppress("DialogTitleCapitalization")
        val action = NotificationGroupManager.getInstance()
            .getNotificationGroup("Kotlin EAP Survey")
            .createNotification(
                KotlinBundle.message("kotlin.eap.survey.notification.title"),
                KotlinBundle.message("kotlin.eap.survey.notification.text"),
                NotificationType.INFORMATION,
            )
            .addAction(
                BrowseNotificationAction(
                    KotlinBundle.message("kotlin.eap.survey.notification.action"),
                    KotlinBundle.message("kotlin.eap.survey.notification.link"),
                )
            )

        action.setIcon(KotlinIcons.SMALL_LOGO)
        action.setImportant(true)
        action.notify(project)
    }
}