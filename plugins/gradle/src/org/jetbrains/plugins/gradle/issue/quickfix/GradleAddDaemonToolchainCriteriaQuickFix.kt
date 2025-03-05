// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue.quickfix

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteria
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import java.util.concurrent.CompletableFuture

class GradleAddDaemonToolchainCriteriaQuickFix(
    private val externalProjectPath: String
) : BuildIssueQuickFix {

    override val id: String = "add_daemon_toolchain_criteria"

    override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
      return GradleDaemonJvmHelper.updateProjectDaemonJvmCriteria(project, externalProjectPath, GradleDaemonJvmCriteria.ANY)
    }
}