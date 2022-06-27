// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration.notifications

import com.intellij.ide.util.RunOnceUtil
import com.intellij.notification.BrowseNotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout

internal fun showEapAdvertisementNotification() {
    val compilerVersion = KotlinPluginLayout.instance.ideCompilerVersion
    if (compilerVersion.kotlinVersion != KotlinVersion(major = 1, minor = 7, patch = 20)) return
    if (compilerVersion.kind != IdeKotlinVersion.Kind.Beta(number = 1)) return

    RunOnceUtil.runOnceForApp("kotlin.eap.advertisement.was.shown.once") {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Kotlin EAP")
            .createNotification(
                KotlinBundle.message("kotlin.eap.advertisement.notification.title"),
                KotlinBundle.message("kotlin.eap.advertisement.notification.text"),
                NotificationType.INFORMATION,
            )
            .addAction(
                BrowseNotificationAction(
                    KotlinBundle.message("kotlin.eap.advertisement.notification.action"),
                    KotlinBundle.message("kotlin.eap.advertisement.notification.link"),
                )
            )
            .setSuggestionType(true)
            .setIcon(KotlinIcons.SMALL_LOGO)
            .setImportant(true)
            .notify(null)
    }
}