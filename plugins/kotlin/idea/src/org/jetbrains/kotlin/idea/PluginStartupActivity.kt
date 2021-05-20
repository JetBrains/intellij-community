/*
 * Copyright 2010-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.kotlin.idea

import com.intellij.ProjectTopics
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.updateSettings.impl.UpdateChecker.excludedFromUpdateCheckPlugins
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinPluginCompatibilityVerifier.checkCompatibility
import org.jetbrains.kotlin.idea.configuration.ui.notifications.notifyKotlinStyleUpdateIfNeeded
import org.jetbrains.kotlin.idea.reporter.KotlinReportSubmitter.Companion.setupReportingFromRelease
import org.jetbrains.kotlin.js.resolve.diagnostics.ErrorsJs
import org.jetbrains.kotlin.resolve.jvm.KotlinJavaPsiFacade
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm
import org.jetbrains.kotlin.resolve.konan.diagnostics.ErrorsNative
import java.util.concurrent.Callable

class PluginStartupActivity : StartupActivity.Background {
    override fun runActivity(project: Project) {
        val startupService = PluginStartupService.getInstance(project)

        startupService.register()
        project.messageBus.connect(startupService).subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
            override fun rootsChanged(event: ModuleRootEvent) {
                KotlinJavaPsiFacade.getInstance(project).clearPackageCaches()
            }
        })
        initializeDiagnostics()
        excludedFromUpdateCheckPlugins.add("org.jetbrains.kotlin")
        checkCompatibility()
        setupReportingFromRelease()
        if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
            runReadAction {
                notifyKotlinStyleUpdateIfNeeded(project)
            }
        }

        //todo[Sedunov]: wait for fix in platform to avoid misunderstood from Java newbies (also ConfigureKotlinInTempDirTest)
        //KotlinSdkType.Companion.setUpIfNeeded();

        ReadAction.nonBlocking(Callable {
            FileTypeIndex.containsFileOfType(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(project))
        })
            .inSmartMode(project)
            .expireWith(startupService)
            .finishOnUiThread(ModalityState.any()) { hasKotlinFiles ->
                if (!hasKotlinFiles) return@finishOnUiThread

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