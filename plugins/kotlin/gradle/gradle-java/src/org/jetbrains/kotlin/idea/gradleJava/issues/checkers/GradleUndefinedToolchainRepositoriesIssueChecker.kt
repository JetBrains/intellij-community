// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.issues.checkers

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesConfigurator
import org.jetbrains.kotlin.idea.gradleJava.issues.quickfix.GradleAddDownloadToolchainRepositoryQuickFix
import org.jetbrains.plugins.gradle.issue.ConfigurableGradleBuildIssue
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler.getRootCauseAndLocation
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.util.function.Consumer

/**
 * An [GradleIssueChecker] handling Daemon JVM criteria exceptions like:
 * "Cannot find a Java installation on your machine (Mac OS X 14.7.1 aarch64) matching: {languageVersion=99, vendor=any vendor,
 * implementation=vendor-specific}. Toolchain download repositories have not been configured."
 */
class GradleUndefinedToolchainRepositoriesIssueChecker : GradleIssueChecker {

    override fun check(issueData: GradleIssueData): BuildIssue? {
        val rootCause = getRootCauseAndLocation(issueData.error).first
        if (rootCause.message?.contains("Toolchain download repositories have not been configured.") == true) {
            return GradleUndefinedToolchainRepositoriesBuildIssue(rootCause, issueData.projectPath)
        }
        return null
    }

    override fun consumeBuildOutputFailureMessage(
        message: String,
        failureCause: String,
        stacktrace: String?,
        location: FilePosition?,
        parentEventId: Any,
        messageConsumer: Consumer<in BuildEvent>
    ): Boolean {
        return parentEventId == ":${DaemonJvmPropertiesConfigurator.TASK_NAME}"
    }
}

private class GradleUndefinedToolchainRepositoriesBuildIssue(
    cause: Throwable,
    externalProjectPath: String
) : ConfigurableGradleBuildIssue() {
    init {
        setTitle(GradleBundle.message("gradle.build.issue.daemon.toolchain.repositories.undefined.title"))
        addDescription(cause.message ?: title)
        run {
            val quickFix = GradleAddDownloadToolchainRepositoryQuickFix(externalProjectPath)
            val hyperlinkReference = addQuickFix(quickFix)
            addQuickFixPrompt(GradleBundle.message("gradle.build.quick.fix.add.toolchain.repository", hyperlinkReference))
        }
    }
}