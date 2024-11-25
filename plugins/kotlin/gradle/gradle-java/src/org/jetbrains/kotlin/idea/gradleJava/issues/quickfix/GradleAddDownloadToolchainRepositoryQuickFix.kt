// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.issues.quickfix

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.project.Project
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptSupport
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getTopLevelBuildScriptSettingsPsiFile
import org.jetbrains.plugins.gradle.service.coroutine.GradleCoroutineScopeProvider
import java.util.concurrent.CompletableFuture

object GradleAddDownloadToolchainRepositoryQuickFix : BuildIssueQuickFix {

    override val id: String = "add_download_toolchain_repository"

    override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
        return GradleCoroutineScopeProvider.getInstance(project).cs
            .launch {
                writeCommandAction(project, "Applying Foojay Plugin to Project") {
                    project.getTopLevelBuildScriptSettingsPsiFile()?.let { topLevelBuildScript ->
                        val buildScriptSupport = GradleBuildScriptSupport.getManipulator(topLevelBuildScript)
                        buildScriptSupport.addFoojayPlugin(topLevelBuildScript)
                    } ?: run {
                        // TODO create settings.gradle file with Foojay Plugin
                    }
                }
            }.asCompletableFuture()
    }
}