// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.base.util.containsKotlinFile

internal class K1PluginStartupActivity : PluginStartupActivity() {
    override suspend fun executeExtraActions(project: Project) {
        val hasKt = smartReadAction(project) {
            project.containsKotlinFile()
        }

        if (hasKt) {
            withContext(Dispatchers.EDT) {
                val daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project) as DaemonCodeAnalyzerImpl
                daemonCodeAnalyzer.serializeCodeInsightPasses(true)
            }
        }
    }
}