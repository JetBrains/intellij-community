// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import com.intellij.ProjectTopics
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.updateSettings.impl.UpdateChecker.excludedFromUpdateCheckPlugins
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinPluginCompatibilityVerifier.checkCompatibility
import org.jetbrains.kotlin.idea.configuration.notifications.notifyKotlinStyleUpdateIfNeeded
import org.jetbrains.kotlin.idea.reporter.KotlinReportSubmitter.Companion.setupReportingFromRelease
import org.jetbrains.kotlin.idea.search.containsKotlinFile
import org.jetbrains.kotlin.js.resolve.diagnostics.ErrorsJs
import org.jetbrains.kotlin.resolve.jvm.KotlinJavaPsiFacade
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm
import org.jetbrains.kotlin.resolve.konan.diagnostics.ErrorsNative
import java.util.concurrent.Callable

internal class PluginStartupActivity : StartupActivity.Background {
    override fun runActivity(project: Project) {
        val startupService = PluginStartupService.getInstance(project)

        startupService.register()
        val connection = project.messageBus.connect(startupService)
        connection.subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
            override fun rootsChanged(event: ModuleRootEvent) {
                KotlinJavaPsiFacade.getInstance(project).clearPackageCaches()
            }
        })
        connection.subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
            override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
                clearPackageCaches()
            }

            override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
                clearPackageCaches()
            }

            override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
                clearPackageCaches()
            }

            private fun clearPackageCaches() {
                KotlinJavaPsiFacade.getInstance(project).clearPackageCaches()
            }
        })

        initializeDiagnostics()
        excludedFromUpdateCheckPlugins.add("org.jetbrains.kotlin")
        checkCompatibility()
        setupReportingFromRelease()

        //todo[Sedunov]: wait for fix in platform to avoid misunderstood from Java newbies (also ConfigureKotlinInTempDirTest)
        //KotlinSdkType.Companion.setUpIfNeeded();

        ReadAction.nonBlocking(Callable { project.containsKotlinFile() })
            .inSmartMode(project)
            .expireWith(startupService)
            .finishOnUiThread(ModalityState.any()) { hasKotlinFiles ->
                if (!hasKotlinFiles) return@finishOnUiThread

                if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
                    notifyKotlinStyleUpdateIfNeeded(project)
                }

                val daemonCodeAnalyzer = DaemonCodeAnalyzerImpl.getInstanceEx(project) as DaemonCodeAnalyzerImpl
                daemonCodeAnalyzer.serializeCodeInsightPasses(true)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    companion object {
        /*
        Concurrent access to Errors may lead to the class loading dead lock because of non-trivial initialization in Errors.
        As a work-around, all Error classes are initialized beforehand.
        It doesn't matter what exact diagnostic factories are used here.
     */
        private fun initializeDiagnostics() {
            consumeFactory(Errors.DEPRECATION)
            consumeFactory(ErrorsJvm.ACCIDENTAL_OVERRIDE)
            consumeFactory(ErrorsJs.CALL_FROM_UMD_MUST_BE_JS_MODULE_AND_JS_NON_MODULE)
            consumeFactory(ErrorsNative.INCOMPATIBLE_THROWS_INHERITED)
        }

        private inline fun consumeFactory(factory: DiagnosticFactory<*>) {
            factory.javaClass
        }
    }
}