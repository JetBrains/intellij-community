// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.application.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.impl.UpdateChecker.excludedFromUpdateCheckPlugins
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.base.util.containsKotlinFile
import org.jetbrains.kotlin.idea.base.util.containsNonScriptKotlinFile
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePluginVersion
import org.jetbrains.kotlin.idea.configuration.notifications.notifyKotlinStyleUpdateIfNeeded
import org.jetbrains.kotlin.idea.configuration.notifications.showEapSurveyNotification
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.reporter.KotlinReportSubmitter.Companion.setupReportingFromRelease
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import java.util.concurrent.Callable

internal class PluginStartupActivity : StartupActivity.Background {
    override fun runActivity(project: Project) {
        excludedFromUpdateCheckPlugins.add("org.jetbrains.kotlin")
        checkPluginCompatibility()
        setupReportingFromRelease()

        //todo[Sedunov]: wait for fix in platform to avoid misunderstood from Java newbies (also ConfigureKotlinInTempDirTest)
        //KotlinSdkType.Companion.setUpIfNeeded();

        val pluginDisposable = KotlinPluginDisposable.getInstance(project)
        ReadAction.nonBlocking(Callable { project.containsKotlinFile() })
            .inSmartMode(project)
            .expireWith(pluginDisposable)
            .finishOnUiThread(ModalityState.any()) { hasKotlinFiles ->
                if (!hasKotlinFiles) return@finishOnUiThread

                val daemonCodeAnalyzer = DaemonCodeAnalyzerImpl.getInstanceEx(project) as DaemonCodeAnalyzerImpl
                daemonCodeAnalyzer.serializeCodeInsightPasses(true)
            }
            .submit(AppExecutorUtil.getAppExecutorService())

        if (ApplicationManager.getApplication().isHeadlessEnvironment) return

        ReadAction.nonBlocking(Callable { project.containsNonScriptKotlinFile() })
            .inSmartMode(project)
            .expireWith(pluginDisposable)
            .finishOnUiThread(ModalityState.any()) { hasKotlinFiles ->
                if (!hasKotlinFiles) return@finishOnUiThread

                notifyKotlinStyleUpdateIfNeeded(project)
                if (!isUnitTestMode()) {
                    showEapSurveyNotification(project)
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun checkPluginCompatibility() {
        val platformVersion = ApplicationInfo.getInstance().shortVersion ?: return
        val isAndroidStudio = KotlinPlatformUtils.isAndroidStudio

        val rawVersion = KotlinIdePlugin.version
        val kotlinPluginVersion = KotlinIdePluginVersion.parse(rawVersion).getOrNull() ?: return

        if (kotlinPluginVersion.platformVersion != platformVersion || kotlinPluginVersion.isAndroidStudio != isAndroidStudio) {
            val ideName = ApplicationInfo.getInstance().versionName

            runInEdt {
                Messages.showWarningDialog(
                    KotlinBundle.message("plugin.verifier.compatibility.issue.message", rawVersion, ideName, platformVersion),
                    KotlinBundle.message("plugin.verifier.compatibility.issue.title")
                )
            }
        }
    }
}