// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration.notifications

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.BrowseNotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.KotlinVersionVerbose
import org.jetbrains.kotlin.idea.configuration.findAnyExternalKotlinCompilerVersion

private const val LAST_BUNDLED_KOTLIN_COMPILER_VERSION_PROPERTY_NAME = "kotlin.updates.whats.new.shown.for"

fun checkExternalKotlinCompilerVersion(project: Project) {
    val bundledKotlinCompilerVersion = bundledKotlinCompilerVersionIfReleased() ?: return
    if (shouldSkip(bundledKotlinCompilerVersion)) return

    val externalKotlinCompilerVersion = runReadAction { project.findAnyExternalKotlinCompilerVersion() }?.plainVersion ?: return
    if (externalKotlinCompilerVersion >= bundledKotlinCompilerVersion) return
    invokeLater {
        disableNewNotificationsForVersion(bundledKotlinCompilerVersion)
    }

    NotificationGroupManager.getInstance()
        .getNotificationGroup("kotlin.external.compiler.updates")
        .createNotification(
            KotlinBundle.message("kotlin.external.compiler.updates.notification.content.0", bundledKotlinCompilerVersion),
            NotificationType.INFORMATION,
        )
        .addAction(createWhatIsNewAction(bundledKotlinCompilerVersion))
        .setIcon(KotlinIcons.SMALL_LOGO)
        .setImportant(true)
        .notify(project)
}

private fun bundledKotlinCompilerVersionIfReleased(): KotlinVersion? {
    val kotlinCompilerVersion = KotlinCompilerVersion.getVersion() ?: return null
    val kotlinVersionVerbose = KotlinVersionVerbose.parse(kotlinCompilerVersion)?.takeIf {
        it.milestone == KotlinVersionVerbose.KotlinVersionMilestone.release
    } ?: return null

    return kotlinVersionVerbose.plainVersion
}

private fun disableNewNotificationsForVersion(kotlinVersion: KotlinVersion): Unit = runWriteAction {
    PropertiesComponent.getInstance().setValue(LAST_BUNDLED_KOTLIN_COMPILER_VERSION_PROPERTY_NAME, kotlinVersion.toString())
}

private fun shouldSkip(bundledKotlinCompilerVersion: KotlinVersion): Boolean {
    val lastVersionValue = runReadAction {
        PropertiesComponent.getInstance().getValue(LAST_BUNDLED_KOTLIN_COMPILER_VERSION_PROPERTY_NAME)
    } ?: return false

    val lastVersion = KotlinVersionVerbose.parse(lastVersionValue)?.plainVersion ?: return false
    return lastVersion >= bundledKotlinCompilerVersion
}

private fun createWhatIsNewAction(kotlinVersion: KotlinVersion): AnAction = BrowseNotificationAction(
    KotlinBundle.message("kotlin.external.compiler.updates.notification.learn.what.is.new.action"),
    whatIsNewPageUrl(kotlinVersion),
)

private fun whatIsNewPageUrl(kotlinVersion: KotlinVersion): String =
    "https://kotlinlang.org/docs/whatsnew${kotlinVersion.whatIsNewPageVersion}.html?utm_source=ide&utm_medium=release-notification&utm_campaign=${kotlinVersion.campaignVersion}-release"

@get:VisibleForTesting
val KotlinVersion.whatIsNewPageVersion: String
    get() = buildString {
        append(major)
        append(minor)
        val majorPartOfPath = patch / 10
        if (majorPartOfPath > 1) {
            append(majorPartOfPath)
            append('0')
        }
    }

@get:VisibleForTesting
val KotlinVersion.campaignVersion: String get() = "$major-$minor-$patch"