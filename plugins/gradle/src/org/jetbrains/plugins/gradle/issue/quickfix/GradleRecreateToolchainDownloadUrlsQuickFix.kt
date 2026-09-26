// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue.quickfix

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

class GradleRecreateToolchainDownloadUrlsQuickFix(
  private val externalProjectPath: String,
) : BuildIssueQuickFix {

  override val id: String = "recreate_toolchain_download_urls"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val daemonJvmProperties = GradleDaemonJvmPropertiesFile.getProperties(Path.of(externalProjectPath))
    return GradleDaemonJvmHelper.updateProjectDaemonJvmCriteria(project, externalProjectPath, daemonJvmProperties.criteria)
  }
}