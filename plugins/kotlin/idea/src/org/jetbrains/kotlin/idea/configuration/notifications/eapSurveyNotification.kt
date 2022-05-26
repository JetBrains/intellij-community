// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration.notifications

import com.intellij.ide.util.RunOnceUtil
import com.intellij.notification.BrowseNotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import java.time.LocalDate

internal fun showEapSurveyNotification(project: Project) {
    if (LocalDate.now() > LocalDate.of(/* year = */ 2022, /* month = */ 5, /* dayOfMonth = */ 8)) return

    val compilerVersion = KotlinPluginLayout.ideCompilerVersion
    if (compilerVersion.kotlinVersion != KotlinVersion(major = 1, minor = 7, patch = 0)) return
    if (compilerVersion.kind != IdeKotlinVersion.Kind.Beta(number = 1)) return

    RunOnceUtil.runOnceForApp("kotlin.eap.survey.was.shown.once") {
        @Suppress("DialogTitleCapitalization")
        NotificationGroupManager.getInstance()
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
            .setSuggestionType(true)
            .setIcon(KotlinIcons.SMALL_LOGO)
            .setImportant(true)
            .notify(project)
    }
}