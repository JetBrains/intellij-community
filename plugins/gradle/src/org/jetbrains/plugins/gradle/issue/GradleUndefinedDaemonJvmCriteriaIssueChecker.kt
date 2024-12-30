// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.issue.BuildIssue
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler.getRootCauseAndLocation
import org.jetbrains.plugins.gradle.util.GradleBundle

/**
 * An [GradleIssueChecker] handling Daemon JVM criteria exception when isn't defined for the project
 */
class GradleUndefinedDaemonJvmCriteriaIssueChecker : GradleIssueChecker {

    override fun check(issueData: GradleIssueData): BuildIssue? {
        val rootCause = getRootCauseAndLocation(issueData.error).first
        if (false) { // TODO: No known exception since right now is an opt-in feature
            return GradleUndefinedDaemonJvmCriteriaBuildIssue(rootCause, issueData.projectPath)
        }
        return null
    }
}

private class GradleUndefinedDaemonJvmCriteriaBuildIssue(
    cause: Throwable,
    externalProjectPath: String
) : ConfigurableGradleBuildIssue() {
    init {
        setTitle(GradleBundle.message("gradle.build.issue.daemon.toolchain.undefined.criteria.title"))
        addDescription(cause.message ?: title)
        addDaemonToolchainCriteriaQuickFix(externalProjectPath)
    }
}