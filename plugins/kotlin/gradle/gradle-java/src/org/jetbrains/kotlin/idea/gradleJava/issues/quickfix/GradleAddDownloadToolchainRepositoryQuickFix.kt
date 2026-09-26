// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.issues.quickfix

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.gradleJava.KotlinGradleCoroutineScopeService.Companion.gradleCoroutineScope
import org.jetbrains.kotlin.idea.gradleJava.toolchain.GradleDaemonJvmCriteriaMigrationHelper
import java.util.concurrent.CompletableFuture

class GradleAddDownloadToolchainRepositoryQuickFix(
    private val externalProjectPath: String
) : BuildIssueQuickFix {

    override val id: String = "add_download_toolchain_repository"

    override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
        return project.gradleCoroutineScope.launch {
            GradleDaemonJvmCriteriaMigrationHelper.applyDefaultToolchainResolverPlugin(project, externalProjectPath)
        }.asCompletableFuture()
    }
}