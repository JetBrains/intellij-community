// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.idea.base.util.containsKotlinFile
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import java.util.concurrent.Callable

internal class K1PluginStartupActivity: PluginStartupActivity() {
    override fun executeExtraActions(project: Project) {
        val pluginDisposable = KotlinPluginDisposable.getInstance(project)
        ReadAction.nonBlocking(Callable { project.containsKotlinFile() })
            .inSmartMode(project)
            .expireWith(pluginDisposable)
            .finishOnUiThread(ModalityState.any()) { hasKotlinFiles ->
                if (!hasKotlinFiles) return@finishOnUiThread

                val daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project) as DaemonCodeAnalyzerImpl
                daemonCodeAnalyzer.serializeCodeInsightPasses(true)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }
}