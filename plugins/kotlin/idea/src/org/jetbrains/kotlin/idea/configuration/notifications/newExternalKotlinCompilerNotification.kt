// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration.notifications

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.BrowseNotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.KotlinVersionVerbose
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.configuration.findAnyExternalKotlinCompilerVersion

@VisibleForTesting
const val LAST_BUNDLED_KOTLIN_COMPILER_VERSION_PROPERTY_NAME = "kotlin.updates.whats.new.shown.for"

class ExternalKotlinCompilerProjectDataImportListener(private val project: Project) : ProjectDataImportListener {
    override fun onImportFinished(projectPath: String?) {
        checkExternalKotlinCompilerVersion(project)
    }
}

fun checkExternalKotlinCompilerVersion(project: Project) {
    val bundledKotlinCompilerVersion = bundledCompilerVersionIfReleased() ?: return
    if (!newExternalKotlinCompilerShouldBePromoted(bundledKotlinCompilerVersion, project::findExternalCompilerVersion)) return

    invokeLater {
        bundledKotlinCompilerVersion.disableNewNotifications()
    }

    NotificationGroupManager.getInstance()
        .getNotificationGroup("kotlin.external.compiler.updates")
        .createNotification(
            KotlinBundle.message("kotlin.external.compiler.updates.notification.content.0", bundledKotlinCompilerVersion),
            NotificationType.INFORMATION,
        )
        .setSuggestionType(true)
        .addAction(createWhatIsNewAction(bundledKotlinCompilerVersion))
        .setIcon(KotlinIcons.SMALL_LOGO)
        .setImportant(true)
        .notify(project)
}

private fun Project.findExternalCompilerVersion(): KotlinVersion? = runReadAction { findAnyExternalKotlinCompilerVersion() }?.plainVersion

private fun bundledCompilerVersionIfReleased(): KotlinVersion? {
    val kotlinCompilerVersion = KotlinPluginLayout.instance.standaloneCompilerVersion
    val kotlinVersionVerbose = KotlinVersionVerbose.parse(kotlinCompilerVersion)?.takeIf {
        it.milestone == KotlinVersionVerbose.KotlinVersionMilestone.release
    } ?: return null

    return kotlinVersionVerbose.plainVersion
}

private fun KotlinVersion.disableNewNotifications(): Unit = runWriteAction {
    PropertiesComponent.getInstance().setValue(LAST_BUNDLED_KOTLIN_COMPILER_VERSION_PROPERTY_NAME, toString())
}

@get:VisibleForTesting
val KotlinVersion.dropHotfixPart: KotlinVersion
    get() = KotlinVersion(
        major = major,
        minor = minor,
        patch = patch.div(10).let { if (it == 1) 0 else it } * 10,
    )

@VisibleForTesting
fun newExternalKotlinCompilerShouldBePromoted(
    bundledCompilerVersion: KotlinVersion,
    externalCompilerVersion: () -> KotlinVersion?,
): Boolean {
    val downgradedBundledKotlinCompilerVersion = bundledCompilerVersion.dropHotfixPart
    val lastBundledVersion = findLastBundledCompilerVersion()
    if (lastBundledVersion != null && lastBundledVersion >= downgradedBundledKotlinCompilerVersion) return false

    val externalKotlinCompilerVersion = externalCompilerVersion() ?: return false
    return externalKotlinCompilerVersion < downgradedBundledKotlinCompilerVersion
}

private fun findLastBundledCompilerVersion(): KotlinVersion? {
    val lastVersionValue = runReadAction {
        PropertiesComponent.getInstance().getValue(LAST_BUNDLED_KOTLIN_COMPILER_VERSION_PROPERTY_NAME)
    } ?: return null

    return KotlinVersionVerbose.parse(lastVersionValue)?.plainVersion
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