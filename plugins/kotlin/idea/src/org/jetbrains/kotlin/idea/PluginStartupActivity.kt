// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.updateSettings.impl.UpdateChecker.excludedFromUpdateCheckPlugins
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.idea.base.util.containsNonScriptKotlinFile
import org.jetbrains.kotlin.idea.configuration.notifications.notifyKotlinStyleUpdateIfNeeded
import org.jetbrains.kotlin.idea.configuration.notifications.showEapSurveyNotification
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import java.util.concurrent.Callable

internal open class PluginStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) : Unit = blockingContext {
        excludedFromUpdateCheckPlugins.add("org.jetbrains.kotlin")

        executeExtraActions(project)

        if (ApplicationManager.getApplication().isHeadlessEnvironment) {
            return@blockingContext
        }

        val pluginDisposable = KotlinPluginDisposable.getInstance(project)
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

    protected open fun executeExtraActions(project: Project) = Unit
}