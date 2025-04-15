// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue.quickfix

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.GradleCoroutineScopeService.Companion.gradleCoroutineScope
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteriaDownloadToolchain
import java.util.concurrent.CompletableFuture

class GradleDownloadToolchainQuickFix(
  private val externalProjectPath: String,
) : BuildIssueQuickFix {

  override val id: String = "download_toolchain"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    return project.gradleCoroutineScope.launch {
      GradleDaemonJvmCriteriaDownloadToolchain.downloadJdkMatchingCriteria(project, externalProjectPath)
    }.asCompletableFuture()
  }
}