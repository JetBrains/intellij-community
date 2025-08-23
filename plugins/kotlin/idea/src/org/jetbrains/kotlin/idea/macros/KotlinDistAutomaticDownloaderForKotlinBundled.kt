// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.macros

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlin.config.JpsPluginSettings
import org.jetbrains.kotlin.idea.base.plugin.KotlinBasePluginBundle
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettingsListener
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings

/**
 * This class detects when KOTLIN_BUNDLED becomes used in libraries (it's deprecated way. Kotlin Wizard doesn't generate such project
 * anymore, KTIJ-429) and if it detects such usages then it starts downloading kotlinc-dist
 *
 * This class monitors two types of triggers:
 * 1. Kotlin JPS plugin version changed (see [KotlinJpsPluginSettings]). Then if KOTLIN_BUNDLED used in any library this class will
 *    download required kotlinc-dist
 * 2. User started using KOTLIN_BUNDLED in libraries. Then if current kotlinc-dist of current Kotlin JPS version isn't yet downloaded,
 *    we should download it.
 */
internal class KotlinDistAutomaticDownloaderForKotlinBundled(
    private val project: Project
) : KotlinCompilerSettingsListener, KotlinBundledUsageDetectorListener {
    override fun <T> settingsChanged(oldSettings: T?, newSettings: T?) {
        if (newSettings !is JpsPluginSettings) return
        downloadKotlinDistIfNeeded(
            KotlinBundledUsageDetector.isKotlinBundledPotentiallyUsedInLibraries(project),
            newSettings.version,
            project,
        )
    }

    override fun kotlinBundledDetected() {
        val jpsVersion = KotlinJpsPluginSettings.jpsVersion(project)
        downloadKotlinDistIfNeeded(isKotlinBundledPotentiallyUsedInLibraries = true, jpsVersion, project)
    }
}

private fun downloadKotlinDistIfNeeded(isKotlinBundledPotentiallyUsedInLibraries: Boolean, @NlsSafe version: String, project: Project) {
    if (version.isNotBlank() && isKotlinBundledPotentiallyUsedInLibraries && !KotlinArtifactsDownloader.isKotlinDistInitialized(version)) {
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, KotlinBasePluginBundle.message("progress.text.downloading.kotlinc.dist"), true) {
                override fun run(indicator: ProgressIndicator) {
                    val dist = KotlinArtifactsDownloader.lazyDownloadAndUnpackKotlincDist(project, version, indicator)
                    if (dist == null) {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Kotlin dist downloading failed")
                            .createNotification(
                                KotlinBasePluginBundle.message("kotlin.dist.downloading.failed"),
                                KotlinArtifactsDownloader.failedToDownloadUnbundledJpsMavenArtifact(
                                    project,
                                    KotlinArtifactConstants.KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID,
                                    version
                                ),
                                NotificationType.ERROR,
                            )
                            .setImportant(true)
                            .setIcon(AllIcons.Ide.FatalError)
                            .notify(project)
                    } else {
                        // Since this dist is used as library we should refresh it
                        KotlinBundledRefresher.requestKotlinDistRefresh(dist.toPath())
                    }
                }
            }
        )
    }
}
